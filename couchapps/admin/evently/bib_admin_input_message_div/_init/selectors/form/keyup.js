function(ev) {
    key = ev.which ? ev.which : window.event.keyCode;

    bib_admin_message_form = $("#bib_admin_input_message_div").find("input")[0];
    admin_message = bib_admin_message_form.value;

    bib_admin_message_target_form = $("#bib_admin_input_message_div").find("input")[1];
    admin_message_target = bib_admin_message_target_form.value;

  // In Chrome, up and down array change the cursor location, always set it to the end
    if ((key == 38) || (key == 40)) {
	bib_admin_message_form.selectionStart = admin_message.length;
	bib_admin_message_form.selectionEnd   = admin_message.length;
	bib_admin_message_target_form.selectionStart = admin_message_target.length;
	bib_admin_message_target_form.selectionEnd   = admin_message_target.length;
    }
    
    $("#message_len").html("len=" + admin_message.length + "/128");
    

    //checkBib(bib_value);
    
    // let the event continue
    return true;
};
