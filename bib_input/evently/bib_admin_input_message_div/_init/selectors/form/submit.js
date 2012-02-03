function() {
    var app = $$(this).app;
    var form = $(this)[0];
    var message_to_store = {};
    var message_body = form["bib_admin_message"].value; 
    var message_target = form["bib_admin_message_target"].value;

    form.reset();

    message_target = parseInt(message_target, 10); //hackish, message_target changes type from string to int
    if (isNaN(message_target)) {
	$.log("target is not valid");
	alert("error, target is not valid");
	return false;
    }

    $.log("1 message: '" + message_body + "' for target=" + message_target);

    message_to_store.message = message_body;
    if (message_target != -1)
	message_to_store.target = message_target;

    $.log("storing: " + JSON.stringify(message_to_store));

    function callback_error(stat, err, reason) {
	$.log("stat" + JSON.stringify(stat));
	$.log("err" + JSON.stringify(err));
	$.log("reason" + JSON.stringify(reason));
	alert("Error adding message to db: " + JSON.stringify(reason));
    }

    function callback_ok() {
	$.log("ok");
    }

    app.db.saveDoc(message_to_store, { success: callback_ok, error: callback_error });

    return false;
};
