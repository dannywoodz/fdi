#[macro_use]
extern crate log;
extern crate getopts;
extern crate libc;
extern crate log4rs;
extern crate rayon;
extern crate regex;
extern crate walkdir;

#[allow(unused_imports)]
use rayon::prelude::*;

use getopts::{Fail, Options};
use regex::Regex;
use std::collections::HashMap;
use std::env;
use std::sync::{Arc, Mutex};
use walkdir::{DirEntry, WalkDir};

use log::LevelFilter;
use log4rs::append::console::ConsoleAppender;
use log4rs::config::{Appender, Config, Root};

mod fingerprint;
use fingerprint as fp;

fn log_init(level: LevelFilter) {
    let stdout = ConsoleAppender::builder().build();
    let config = Config::builder()
        .appender(Appender::builder().build("stdout", Box::new(stdout)))
        .build(Root::builder().appender("stdout").build(level))
        .unwrap();

    log4rs::init_config(config).unwrap();
}

fn usage(prog_name: &str, opts: &Options, err: Option<Fail>, exit_code: i32) -> ! {
    let brief = format!("Usage: {} [--threshold=5] dir-1 [..dir-n]", prog_name);
    match err {
        Some(f) => println!("{}", f),
        None => (),
    };
    print!("{}", opts.usage(&brief));
    std::process::exit(exit_code);
}

fn main() {
    let args: Vec<String> = env::args().collect();
    let mut opts = Options::new();
    opts.optopt(
        "t",
        "threshold",
        "threshold for determining image similarity (default=5)",
        "INTEGER",
    );
    opts.optflag("d", "debug", "set loger to DEBUG level");
    let matches = match opts.parse(&args[1..]) {
        Ok(m) => m,
        Err(f) => usage(&args[0], &opts, Some(f), -1),
    };

    let log_level = if matches.opt_present("d") {
        LevelFilter::Debug
    } else {
        LevelFilter::Info
    };

    log_init(log_level);

    let directories = &matches.free;

    if directories.is_empty() {
        usage(&args[0], &opts, None, -2)
    }

    let threshold = match matches.opt_str("t") {
        Some(value) => value.parse::<u32>().unwrap(),
        None => 5,
    };
    let is_hidden = |entry: &DirEntry| -> bool {
        entry
            .file_name()
            .to_str()
            .map(|s| s.starts_with("."))
            .unwrap_or(false)
    };

    let img_extension_rx = Regex::new(r"\.(?:jpg|jpeg|png)$").unwrap();

    let is_image_file = |entry: &DirEntry| -> bool {
        match entry.file_name().to_str() {
            Some(filename) => img_extension_rx.find(filename).is_some(),
            None => false,
        }
    };

    let entries: Vec<DirEntry> = directories
        .iter()
        .flat_map(|directory| {
            info!("Scanning {}", directory);
            WalkDir::new(directory)
                .into_iter()
                .filter_entry(|e| !is_hidden(e) && (e.path().is_dir()) || is_image_file(e))
                .map(|r| r.ok().unwrap())
                .filter(|e| !e.path().is_dir())
        })
        .collect();

    info!("Building fingerprint set for {} images", entries.len());
    let count = Mutex::new(0 as u32);
    let prints: Vec<fp::Fingerprint> = entries
        .par_iter()
        .map(|e| fp::Fingerprint::load(&e))
        .filter(|o| o.is_some())
        .map(|o| o.unwrap())
        .map(|v| {
            let current = {
                let mut holder = count.lock().unwrap();
                let val = *holder;
                *holder += 1;
                val
            };
            if current % 1000 == 999 {
                info!("Loaded {} fingerprints...", current + 1);
            }
            v
        })
        .collect();

    let similar = Arc::new(Mutex::new(HashMap::new()));
    let view = similar.clone();

    info!("Grouping fingerprints by similarity");
    let count = Mutex::new(0 as u32);
    let report_threshold = 1000;

    prints.par_iter().enumerate().for_each(|(idx, print)| {
        for candidate in &prints[idx + 1..] {
            if !candidate.error {
                let count = {
                    let mut holder = count.lock().unwrap();
                    let count = *holder;
                    *holder += 1;
                    count
                };
                if count % report_threshold == report_threshold - 1 {
                    info!("Processed {} of {} images", report_threshold, prints.len());
                }
                if print.is_similar(&candidate, threshold) {
                    let mut map = view.lock().unwrap();
                    if !map.contains_key(&print.path) {
                        map.insert(print.path.clone(), vec![]);
                    }
                    let list = map.get_mut(&print.path).unwrap();
                    list.push(candidate.path.clone());
                }
            }
        }
    });

    let similar = similar.lock().unwrap();

    info!("Generating report");

    similar.iter().for_each(|entry| {
        let (path, similars) = entry;
        print!("{}", path);
        similars.iter().for_each(|p| {
            print!("\t{}", p);
        });
        println!();
    });

    info!("Done");
}
