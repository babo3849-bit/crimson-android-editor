from pathlib import Path

p = Path("native/src/lib.rs")
s = p.read_text()
start = s.find("Java_com_crimson_editor_NativeBridge_setStackCount")
end = s.find("Java_com_crimson_editor_NativeBridge_removeDuplicates", start)
if start < 0 or end < 0:
    raise SystemExit("Could not locate setStackCount JNI function")
part = s[start:end]
if '"verified":true' in part or "verified\\\":true" in part:
    print("Stack-count readback verification already present")
else:
    marker = "            unsafe { crimson_save_free(verify); }"
    if marker not in part:
        raise SystemExit("Could not locate exported-save verification cleanup; refusing to patch")
    check = """            let verify_result = (|| -> Result<(), String> {
                let mut verified_count = 0usize;
                let mut verified_version = 0u64;
                let size_rc = unsafe { crimson_save_list_inventory_items(verify, ptr::null_mut(), 0, &mut verified_count, &mut verified_version) };
                if size_rc != 0 && verified_count == 0 { return Err(format!("Inventarprüfung fehlgeschlagen (Code {size_rc})")); }
                let mut verified: Vec<InventoryItemRecord> = Vec::with_capacity(verified_count);
                let read_rc = unsafe { crimson_save_list_inventory_items(verify, verified.as_mut_ptr(), verified_count, &mut verified_count, &mut verified_version) };
                if read_rc != 0 { return Err(format!("Inventarprüfung fehlgeschlagen (Code {read_rc})")); }
                unsafe { verified.set_len(verified_count); }
                if !verified.iter().any(|r| r.block_idx == block_idx as u32
                    && r.inventory_element_idx == inventory_idx as u32
                    && r.item_element_idx == item_idx as u32
                    && r.stack_count == count as u64) {
                    return Err("Export erneut geöffnet, aber geänderte Stückzahl nicht bestätigt".into());
                }
                Ok(())
            })();
"""
    part = part.replace(marker, check + marker, 1)
    part = part.replace('            Ok(format!("{\\"ok\\":true,\\"stackCount\\":{count}}}"))',
                        '            verify_result?;\n            Ok(format!("{\\"ok\\":true,\\"stackCount\\":{count},\\"verified\\":true}}"))', 1)
    if "verify_result?;" not in part:
        # Insert the propagation immediately after freeing the verification handle.
        part = part.replace(marker, marker + "\n            verify_result?;", 1)
    s = s[:start] + part + s[end:]
    p.write_text(s)
    print("Added exported-save inventory readback verification for edited stack count")
