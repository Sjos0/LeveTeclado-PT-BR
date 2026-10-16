use std::sync::Mutex;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jbyteArray, jobjectArray, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

use crate::dictionary::Dictionary;
use crate::suggest::{get_auto_correction, get_suggestions};

static GLOBAL_DICT: Mutex<Option<Dictionary>> = Mutex::new(None);

/// Trava o dicionário global. Se alguma thread tiver entrado em pânico com o lock
/// (mutex envenenado), usa o conteúdo mesmo assim, em vez de derrubar o processo do teclado.
fn lock_dict() -> std::sync::MutexGuard<'static, Option<Dictionary>> {
    GLOBAL_DICT.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

#[no_mangle]
pub extern "system" fn Java_com_example_keyboard_NativeEngine_nativeInit(
    env: JNIEnv,
    _class: JClass,
    data: jbyteArray,
) -> jboolean {
    if data.is_null() {
        return JNI_FALSE;
    }

    let bytes = match env.convert_byte_array(unsafe { &jni::objects::JByteArray::from_raw(data) }) {
        Ok(b) => b,
        Err(_) => return JNI_FALSE,
    };

    match Dictionary::from_bytes(&bytes) {
        Ok(dict) => {
            let mut guard = lock_dict();
            *guard = Some(dict);
            JNI_TRUE
        }
        Err(_) => JNI_FALSE,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_keyboard_NativeEngine_nativeGetSuggestions(
    mut env: JNIEnv,
    _class: JClass,
    prev_word: JString,
    prefix: JString,
) -> jobjectArray {
    let prev_str: String = if !prev_word.is_null() {
        env.get_string(&prev_word).map(|s| s.into()).unwrap_or_default()
    } else {
        String::new()
    };

    let prefix_str: String = if !prefix.is_null() {
        env.get_string(&prefix).map(|s| s.into()).unwrap_or_default()
    } else {
        String::new()
    };

    let guard = lock_dict();
    let suggestions = if let Some(dict) = guard.as_ref() {
        get_suggestions(dict, &prev_str, &prefix_str)
    } else {
        Vec::new()
    };

    let string_class = match env.find_class("java/lang/String") {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };

    let empty_string = match env.new_string("") {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };

    let array = match env.new_object_array(suggestions.len() as i32, &string_class, &empty_string) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };

    for (i, sug) in suggestions.iter().enumerate() {
        if let Ok(j_sug) = env.new_string(sug) {
            let _ = env.set_object_array_element(&array, i as i32, &j_sug);
        }
    }

    array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_keyboard_NativeEngine_nativeGetAutoCorrection(
    mut env: JNIEnv,
    _class: JClass,
    prev_word: JString,
    typed_word: JString,
) -> jstring {
    let prev_str: String = if !prev_word.is_null() {
        env.get_string(&prev_word).map(|s| s.into()).unwrap_or_default()
    } else {
        String::new()
    };

    let typed_str: String = if !typed_word.is_null() {
        env.get_string(&typed_word).map(|s| s.into()).unwrap_or_default()
    } else {
        String::new()
    };

    let guard = lock_dict();
    if let Some(dict) = guard.as_ref() {
        if let Some(correction) = get_auto_correction(dict, &prev_str, &typed_str) {
            if let Ok(j_str) = env.new_string(&correction) {
                return j_str.into_raw();
            }
        }
    }

    std::ptr::null_mut()
}

#[no_mangle]
pub extern "system" fn Java_com_example_keyboard_NativeEngine_nativeIsWord(
    mut env: JNIEnv,
    _class: JClass,
    word: JString,
) -> jboolean {
    if word.is_null() {
        return JNI_FALSE;
    }

    let word_str: String = env.get_string(&word).map(|s| s.into()).unwrap_or_default();
    let guard = lock_dict();
    if let Some(dict) = guard.as_ref() {
        if dict.is_word(&word_str) {
            return JNI_TRUE;
        }
    }

    JNI_FALSE
}
