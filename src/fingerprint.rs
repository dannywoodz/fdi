use std;
use std::ffi::CString;
use std::os::raw::c_char;
use std::sync::{Once, ONCE_INIT};
use walkdir::DirEntry;

static START : Once = ONCE_INIT;

#[link(name = "fdi")]
extern {
    fn fdi_init();
    fn fdi_fingerprint(filename: *const c_char, fingerprint: *mut c_char) -> i32;
}

pub struct Fingerprint {
    pub path: String,
    pub fingerprint: [u8;48],
    pub error: bool,
}

impl std::cmp::PartialEq for Fingerprint {
    fn eq(&self, other: &Fingerprint) -> bool {
        self.path == other.path
    }
}

impl Fingerprint {
    pub fn is_similar(&self, o: &Fingerprint, tolerance: u32) -> bool {
        let mut error = 0;
        let cutoff = self.fingerprint.len() as u32 * tolerance;
        for it in self.fingerprint.iter().zip(o.fingerprint.iter()) {
            let (v1, v2) = it;
            let v1 = *v1 as i32;
            let v2 = *v2 as i32;
            error = error + (v1 - v2).abs() as u32;
        }
        error < cutoff
    }

    pub fn load(entry: &DirEntry) -> Option<Fingerprint> {
        START.call_once(|| unsafe { fdi_init(); });
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
}
