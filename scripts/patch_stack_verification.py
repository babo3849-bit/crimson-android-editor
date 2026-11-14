from pathlib import Path

p = Path("native/src/lib.rs")
s = p.read_text()
old = """            let mut verify: *mut SaveHandle = ptr::null_mut();
            let rc = unsafe { crimson_save_load_from_file(out_path.as_ptr(), &mut verify) };
            if rc != 0 || verify.is_null() { return Err(format!("Exportprüfung fehlgeschlagen (Code {rc})")); }
            unsafe { crimson_save_free(verify); }
            Ok(format!("{{\\"ok\\":true,\\"stackCount\\":{count}}}"))"""
new = """            let mut verify: *mut SaveHandle = ptr::null_mut();
            let rc = unsafe { crimson_save_load_from_file(out_path.as_ptr(), &mut verify) };
            if rc != 0 || verify.is_null() { return Err(format!("Exportprüfung fehlgeschlagen (Code {rc})")); }
            let verify_result = (|| -> Result<(), String> {
                let mut verified_count = 0usize;
                let mut verified_version = 0u64;
                let size_rc = unsafe { crimson_save_list_inventory_items(verify, ptr::null_mut(), 0, &mut verified_count, &mut verified_version) };
                if size_rc != 0 && verified_count == 0 { return Err(format!("Inventarprüfung fehlgeschlagen (Code {size_rc})")); }
                let mut verified: Vec<InventoryItemRecord> = Vec::with_capacity(verified_count);
                let read_rc = unsafe { crimson_save_list_inventory_items(verify, verified.as_mut_ptr(), verified_count, &mut verified_count, &mut verified_version) };
                if read_rc != 0 { return Err(format!("Inventarprüfung fehlgeschlagen (Code {read_rc})")); }
                unsafe { verified.set_len(verified_count); }
                let found = verified.iter().any(|r| r.block_idx == block_idx as u32
                    && r.inventory_element_idx == inventory_idx as u32
                    && r.item_element_idx == item_idx as u32
                    && r.stack_count == count as u64);
                if !found { return Err("Export erneut geöffnet, aber geänderte Stückzahl nicht bestätigt".into()); }
                Ok(())
            })();
            unsafe { crimson_save_free(verify); }
            verify_result?;
            Ok(format!("{{\\"ok\\":true,\\"stackCount\\":{count},\\"verified\\":true}}"))"""
if old not in s:
    if '"verified":true' in s:
        print("Stack-count readback verification already present")
    else:
        raise SystemExit("Could not find expected setStackCount verification block; refusing to patch")
else:
    p.write_text(s.replace(old, new, 1))
    print("Added exported-save inventory readback verification for edited stack count")
