extern crate walkdir;
extern crate rayon;
extern crate regex;
extern crate libc;

#[allow(unused_imports)]
use rayon::prelude::*;

use std::ffi::CString;
use std::os::raw::c_char;
use std::collections::{HashMap,HashSet};
use std::sync::{Mutex,Arc};
use walkdir::{DirEntry, WalkDir};
use regex::Regex;

struct Fingerprint {
    path: String,
    fingerprint: [u8;48],
    error: bool,
}


#[link(name = "fdi")]
extern {
    fn fdi_init();
    fn fdi_fingerprint(filename: *const c_char, fingerprint: *mut c_char) -> i32;
}

impl std::cmp::PartialEq for Fingerprint {
    fn eq(&self, other: &Fingerprint) -> bool {
        self.path == other.path
    }
}

fn fingerprint(entry: &DirEntry) -> Fingerprint {
    let mut buffer = [0 as u8;48];
    let filename = entry.path().to_str().unwrap();
    let c_filename = CString::new(filename).unwrap();
    let result = unsafe {
        fdi_fingerprint(c_filename.as_ptr(), buffer.as_mut_ptr() as *mut c_char)
    };
    Fingerprint{
        path: filename.to_owned(),
        fingerprint: buffer,
        error: result < 0,
    }
}

fn is_similar(f1: &Fingerprint, f2: &Fingerprint) -> bool {
    let tolerance = 5;
    let mut error = 0;
    let cutoff = (f1.fingerprint.len() * tolerance) as i32;
    for it in f1.fingerprint.iter().zip(f2.fingerprint.iter()) {
        let (v1, v2) = it;
        error = error + (*v1 as i32 - *v2 as i32).abs()
    }
    error < cutoff
}

fn main() {
    let directory = std::env::args().nth(1).expect("Missing directory");

    let is_hidden = | entry: &DirEntry | -> bool {
        entry.file_name()
            .to_str()
            .map(|s| s.starts_with("."))
            .unwrap_or(false)
    };

    let img_extension_rx = Regex::new(r"\.jpg$").unwrap();

    let is_image_file = | entry: &DirEntry | -> bool {
        match entry.file_name().to_str() {
            Some(filename) => img_extension_rx.find(filename).is_some(),
            None => false
        }
    };

    unsafe {
        fdi_init();
    }

    let entries : Vec<DirEntry> = WalkDir::new(directory).into_iter()
        .filter_entry(|e| !is_hidden(e) && (e.path().is_dir()) || is_image_file(e))
        .map(|r| r.ok().unwrap())
        .filter(|e| !e.path().is_dir())
        .collect();

    let prints : Vec<Fingerprint> = entries.par_iter()
        .map(|e| fingerprint(&e))
        .collect();
    let pref = &prints;

    let similar : Arc<Mutex<HashMap<String,Vec<String>>>> = Arc::new(Mutex::new(HashMap::new()));
    let view = similar.clone();

    prints.par_iter().for_each(move |print| {
        for candidate in pref {
                if !candidate.error && candidate != print {
                    if is_similar(&print, &candidate) {
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

    let similar = similar.lock().unwrap();
    
    // let similar = {
    //     let mut similar = similar.lock().unwrap();
    //     let keys = similar.keys().map(|e| e.clone()).collect::<HashSet<String>>();
        
    //     for bucket in similar.values_mut() {
    //         bucket.retain(|s| !keys.contains(s));
    //     }
    //     similar
    // };

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
