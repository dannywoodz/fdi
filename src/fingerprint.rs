use std;
use std::ffi::CString;
use std::os::raw::c_char;
use std::sync::Once;
use walkdir::DirEntry;

static START: Once = Once::new();

#[link(name = "fdi")]
extern "C" {
    fn fdi_init();
    fn fdi_fingerprint(filename: *const c_char, fingerprint: *mut c_char) -> i32;
}

pub struct Fingerprint {
    pub path: String,
    pub fingerprint: [u8; 48],
    pub error: bool,
}

impl std::cmp::PartialEq for Fingerprint {
    fn eq(&self, other: &Fingerprint) -> bool {
        self.path == other.path
    }
}

impl Fingerprint {
    pub fn is_similar(&self, o: &Fingerprint, tolerance: u32) -> bool {
        let cutoff = self.fingerprint.len() as u32 * tolerance;
        self.difference(o) < cutoff
    }

    fn difference(&self, o: &Fingerprint) -> u32 {
        let mut error = 0;
        for it in self.fingerprint.iter().zip(o.fingerprint.iter()) {
            let (v1, v2) = it;
            let v1 = *v1 as i32;
            let v2 = *v2 as i32;
            error = error + (v1 - v2).abs() as u32;
        }
        error
    }

    pub fn load(entry: &DirEntry) -> Option<Fingerprint> {
        START.call_once(|| unsafe {
            fdi_init();
        });
        match entry.path().to_str() {
            Some(filename) => {
                let mut buffer = [0 as u8; 48];
                let c_filename = CString::new(filename).unwrap();
                let result = unsafe {
                    fdi_fingerprint(c_filename.as_ptr(), buffer.as_mut_ptr() as *mut c_char)
                };
                Some(Fingerprint {
                    path: filename.to_owned(),
                    fingerprint: buffer,
                    error: result < 0,
                })
            }
            None => None,
        }
    }
}

#[cfg(test)]
mod test {
    #[test]
    fn test_identical_prints() {
        let data: [u8; 48] = [
            0xa5, 0xce, 0xb8, 0x94, 0x73, 0x84, 0xd2, 0xe3, 0xe0, 0xb6, 0xb6, 0xd1, 0x6f, 0x80,
            0x3c, 0x63, 0x2f, 0x30, 0x72, 0x84, 0x74, 0x64, 0x58, 0x69, 0x37, 0x43, 0x3b, 0x2a,
            0x2a, 0x30, 0x8c, 0xb2, 0x88, 0x7a, 0x65, 0x71, 0x89, 0x93, 0x7d, 0x7a, 0x6e, 0x75,
            0x4f, 0x4f, 0x91, 0x59, 0x80, 0x89,
        ];
        let print1 = ::fingerprint::Fingerprint {
            path: "first".to_string(),
            fingerprint: data,
            error: false,
        };
        let print2 = ::fingerprint::Fingerprint {
            path: "second".to_string(),
            fingerprint: data,
            error: false,
        };
        assert!(print1.is_similar(&print2, 5));
        assert_eq!(print1.difference(&print2), 0);
    }
}
