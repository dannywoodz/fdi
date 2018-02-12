extern crate walkdir;
extern crate rayon;
extern crate regex;
extern crate libc;
extern crate getopts;

#[allow(unused_imports)]
use rayon::prelude::*;

use std::env;
use std::collections::{HashMap,HashSet};
use std::sync::{Mutex,Arc};
use getopts::{Options,Fail};
use walkdir::{DirEntry, WalkDir};
use regex::Regex;

mod fingerprint;
use fingerprint as fp;

fn usage(prog_name: &str, opts: &Options, err: Option<Fail>, exit_code: i32) -> ! {
    let brief = format!("Usage: {} [--threshold=5] dir-1 [..dir-n]", prog_name);
    match err {
        Some(f) => println!("{}", f),
        None => ()
    };
    print!("{}", opts.usage(&brief));
    std::process::exit(exit_code);
}

fn main() {
    let args : Vec<String> = env::args().collect();
    let mut opts = Options::new();
    opts.optopt("t", "threshold", "threshold for determining image similarity (default=5)", "INTEGER");
    let matches = match opts.parse(&args[1..]) {
        Ok(m) => m,
        Err(f) => usage(&args[0], &opts, Some(f), -1)
    };
    let directories = &matches.free;

    if directories.is_empty() {
        usage(&args[0], &opts, None, -2)
    }
    
    let threshold = match matches.opt_str("t") {
        Some(value) => value.parse::<u32>().unwrap(),
        None => 5
    };
    let is_hidden = | entry: &DirEntry | -> bool {
        entry.file_name()
            .to_str()
            .map(|s| s.starts_with("."))
            .unwrap_or(false)
    };

    let img_extension_rx = Regex::new(r"\.(?:jpg|jpeg|png)$").unwrap();

    let is_image_file = | entry: &DirEntry | -> bool {
        match entry.file_name().to_str() {
            Some(filename) => img_extension_rx.find(filename).is_some(),
            None => false
        }
    };

    let entries : Vec<DirEntry> = directories.iter()
        .flat_map(|directory| {
            WalkDir::new(directory).into_iter()
                .filter_entry(|e| !is_hidden(e) && (e.path().is_dir()) || is_image_file(e))
                .map(|r| r.ok().unwrap())
                .filter(|e| !e.path().is_dir())
        })
        .collect();

    let prints : Vec<fp::Fingerprint> = entries.par_iter()
        .map(|e| fp::Fingerprint::load(&e))
        .filter(|o| o.is_some())
        .map(|o| o.unwrap())
        .collect();

    let similar : Arc<Mutex<HashMap<String,Vec<String>>>> = Arc::new(Mutex::new(HashMap::new()));
    let view = similar.clone();

    prints.par_iter().for_each(|print| {
        for candidate in &prints {
                if !candidate.error && candidate != print {
                    if print.is_similar(&candidate, threshold) {
                        let mut map = view.lock().unwrap();
                        if !map.contains_key(&print.path) {
                            map.insert(print.path.clone(), vec!());
                        }
                        let list = map.get_mut(&print.path).unwrap();
                        list.push(candidate.path.clone());
                    }
                }
        }
    });

    let similar = {
        let mut similar = similar.lock().unwrap();
        let mut keys : HashSet<String> = HashSet::new();
        for (_key,bucket) in similar.iter_mut() {
            bucket.retain(|s| !keys.contains(s));
            for k in bucket {
                keys.insert(k.clone());
            }
        }
        similar
    };

    similar.iter()
        .filter(|entry| {
            let &(_, similars) = entry;
            similars.len() > 1
        })
        .for_each(|entry| {
            let (path, similars) = entry;
            print!("{}", path);
            similars.iter().for_each(|p| { print!("\t{}", p); });
            println!();
        });
}
