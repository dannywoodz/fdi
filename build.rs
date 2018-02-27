extern crate cc;
extern crate pkg_config;

fn main() {
    let magick = pkg_config::Config::new().atleast_version("7").probe("MagickWand").unwrap();
    let hdri_enable = if magick.libs.iter().any(|s| s.find("HDRI").is_some()) {
        "1"
    } else {
        "0"
    };
    let mut builder = cc::Build::new();
    builder
        .define("MAGICKCORE_HDRI_ENABLE", hdri_enable)
        .define("MAGICKCORE_QUANTUM_DEPTH","16");
    for path in &magick.include_paths {
        builder.include(path);
    }
    builder
        .file("src/fdi.c")
        .compile("fdi");
}
