//! C-ABI + JNI shim over naga: WGSL text in, SPIR-V words out. The whole surface is two
//! operations (compile, validate) because that is all the runtime path needs -- everything
//! else stays with the build-time naga-cli pipeline.

use naga::back::spv;
use naga::front::wgsl;
use naga::valid::{Capabilities, ValidationFlags, Validator};

fn compile(source: &str) -> Result<Vec<u32>, String> {
    let module = wgsl::parse_str(source).map_err(|e| e.emit_to_string(source))?;
    let info = Validator::new(ValidationFlags::all(), Capabilities::all())
        .validate(&module)
        .map_err(|e| e.emit_to_string(source))?;
    // No pipeline options: the emitted module carries every entry point, matching
    // VkShaderModule's own multi-entry-point model.
    //
    // ADJUST_COORDINATE_SPACE off -- the naga-cli `--keep-coordinate-space` this replaced.
    // It is on by default and flips Y, which renders every shader upside down; the engine's
    // shaders already emit Vulkan clip space themselves.
    let mut options = spv::Options::default();
    options
        .flags
        .remove(spv::WriterFlags::ADJUST_COORDINATE_SPACE);
    spv::write_vec(&module, &info, &options, None).map_err(|e| e.to_string())
}

fn validate(source: &str) -> Option<String> {
    match wgsl::parse_str(source) {
        Err(e) => Some(e.emit_to_string(source)),
        Ok(module) => Validator::new(ValidationFlags::all(), Capabilities::all())
            .validate(&module)
            .err()
            .map(|e| e.emit_to_string(source)),
    }
}

// ---------------------------------------------------------------------------------------
// C ABI (iOS cinterop): caller frees the returned buffer with awake_naga_free.
// ---------------------------------------------------------------------------------------

use std::ffi::{c_char, CStr, CString};

/// Compiles WGSL to SPIR-V. Returns a malloc'd byte buffer (little-endian u32 words) and
/// writes its length to `out_len`; returns null on error and writes a malloc'd message to
/// `out_error` instead. Exactly one of buffer/error is non-null.
#[no_mangle]
pub unsafe extern "C" fn awake_naga_wgsl_to_spirv(
    source: *const c_char,
    out_len: *mut usize,
    out_error: *mut *mut c_char,
) -> *mut u8 {
    *out_error = std::ptr::null_mut();
    *out_len = 0;
    let Ok(source) = CStr::from_ptr(source).to_str() else {
        *out_error = CString::new("source is not UTF-8").unwrap().into_raw();
        return std::ptr::null_mut();
    };
    match compile(source) {
        Ok(words) => {
            let bytes: Vec<u8> = words.iter().flat_map(|w| w.to_le_bytes()).collect();
            *out_len = bytes.len();
            let boxed = bytes.into_boxed_slice();
            Box::into_raw(boxed) as *mut u8
        }
        Err(message) => {
            *out_error = CString::new(message).unwrap_or_default().into_raw();
            std::ptr::null_mut()
        }
    }
}

/// Validates WGSL. Returns null when valid, else a malloc'd error message the caller frees
/// with awake_naga_free_string.
#[no_mangle]
pub unsafe extern "C" fn awake_naga_validate(source: *const c_char) -> *mut c_char {
    let Ok(source) = CStr::from_ptr(source).to_str() else {
        return CString::new("source is not UTF-8").unwrap().into_raw();
    };
    match validate(source) {
        Some(message) => CString::new(message).unwrap_or_default().into_raw(),
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub unsafe extern "C" fn awake_naga_free(buffer: *mut u8, len: usize) {
    if !buffer.is_null() {
        drop(Box::from_raw(std::slice::from_raw_parts_mut(buffer, len)));
    }
}

#[no_mangle]
pub unsafe extern "C" fn awake_naga_free_string(message: *mut c_char) {
    if !message.is_null() {
        drop(CString::from_raw(message));
    }
}

// ---------------------------------------------------------------------------------------
// JNI (desktop JVM + Android): symbols matched by NagaJni's external functions.
// ---------------------------------------------------------------------------------------

#[cfg(any(
    target_os = "macos",
    target_os = "linux",
    target_os = "windows",
    target_os = "android"
))]
mod jni_bindings {
    use super::{compile, validate};
    use jni::objects::{JClass, JString};
    use jni::sys::{jbyteArray, jstring};
    use jni::JNIEnv;

    fn throw(env: &mut JNIEnv, message: &str) {
        let _ = env.throw_new("io/github/awakelab/awake/asset/shadercompiler/NagaException", message);
    }

    #[no_mangle]
    pub extern "system" fn Java_io_github_awakelab_awake_asset_shadercompiler_NagaJni_wgslToSpirv(
        mut env: JNIEnv,
        _class: JClass,
        source: JString,
    ) -> jbyteArray {
        let source: String = match env.get_string(&source) {
            Ok(s) => s.into(),
            Err(e) => {
                throw(&mut env, &e.to_string());
                return std::ptr::null_mut();
            }
        };
        match compile(&source) {
            Ok(words) => {
                let bytes: Vec<u8> = words.iter().flat_map(|w| w.to_le_bytes()).collect();
                match env.byte_array_from_slice(&bytes) {
                    Ok(array) => array.into_raw(),
                    Err(e) => {
                        throw(&mut env, &e.to_string());
                        std::ptr::null_mut()
                    }
                }
            }
            Err(message) => {
                throw(&mut env, &message);
                std::ptr::null_mut()
            }
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_io_github_awakelab_awake_asset_shadercompiler_NagaJni_validate(
        mut env: JNIEnv,
        _class: JClass,
        source: JString,
    ) -> jstring {
        let source: String = match env.get_string(&source) {
            Ok(s) => s.into(),
            Err(e) => {
                throw(&mut env, &e.to_string());
                return std::ptr::null_mut();
            }
        };
        match validate(&source) {
            Some(message) => match env.new_string(message) {
                Ok(s) => s.into_raw(),
                Err(_) => std::ptr::null_mut(),
            },
            None => std::ptr::null_mut(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const TRIANGLE: &str = r#"
@vertex fn vertexMain(@location(0) p : vec3f) -> @builtin(position) vec4f {
  return vec4f(p, 1.0);
}
@fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(1.0); }
"#;

    #[test]
    fn compiles_valid_wgsl_to_spirv_magic() {
        let words = compile(TRIANGLE).expect("valid WGSL must compile");
        assert_eq!(words[0], 0x0723_0203, "SPIR-V magic number");
    }

    #[test]
    fn validate_reports_errors_and_accepts_valid() {
        assert!(validate(TRIANGLE).is_none());
        assert!(validate("fn broken( {").is_some());
    }
}
