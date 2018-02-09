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

fn fingerprint(entry: &DirEntry) -> Option<Fingerprint> {
    match entry.path().to_str() {
        Some(filename) => {
            let mut buffer = [0 as u8;48];
            let c_filename = CString::new(filename).unwrap();
            let result = unsafe {
                fdi_fingerprint(c_filename.as_ptr(), buffer.as_mut_ptr() as *mut c_char)
            };
            Some(Fingerprint{
                path: filename.to_owned(),
                fingerprint: buffer,
                error: result < 0,
            })
        },
        None => None
    }
}

impl Fingerprint {
    fn is_similar(&self, o: &Fingerprint) -> bool {
        let tolerance = 5;
        let mut error = 0;
        let cutoff = (self.fingerprint.len() * tolerance) as i32;
        for it in self.fingerprint.iter().zip(o.fingerprint.iter()) {
            let (v1, v2) = it;
            let v1 = *v1 as i32;
            let v2 = *v2 as i32;
            error = error + (v1 - v2).abs()
        }
        error < cutoff
    }
}

fn main() {

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

    let entries : Vec<DirEntry> = std::env::args()
        .flat_map(|directory| {
            WalkDir::new(directory).into_iter()
                .filter_entry(|e| !is_hidden(e) && (e.path().is_dir()) || is_image_file(e))
                .map(|r| r.ok().unwrap())
                .filter(|e| !e.path().is_dir())
        })
        .collect();

    let prints : Vec<Fingerprint> = entries.par_iter()
        .map(|e| fingerprint(&e))
        .filter(|o| o.is_some())
        .map(|o| o.unwrap())
        .collect();
    let pref = &prints;

    let similar : Arc<Mutex<HashMap<String,Vec<String>>>> = Arc::new(Mutex::new(HashMap::new()));
    let view = similar.clone();

    prints.par_iter().for_each(move |print| {
        for candidate in pref {
                if !candidate.error && candidate != print {
                    if print.is_similar(&candidate) {
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
