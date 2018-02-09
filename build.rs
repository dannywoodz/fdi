extern crate gcc;

fn main() {
    println!("cargo:rustc-link-lib=dylib=MagickWand-7.Q16");
    gcc::Build::new()
        .define("MAGICKCORE_HDRI_ENABLE","0")
        .define("MAGICKCORE_QUANTUM_DEPTH","16")
        .include("/usr/include/ImageMagick-7")
        .flag("-fopenmp")
        .file("src/fdi.c")
        .compile("fdi");
}
