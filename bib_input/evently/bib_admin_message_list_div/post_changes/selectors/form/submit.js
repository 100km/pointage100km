function() {
    var app = $$(this).app;
    var form = $(this)[0];
    var message_to_modify = {};
    var message_to_delete = {};
    var message_id     = form["id"].value;
    var message_rev    = form["rev"].value;
    var message_target = form["target"].value;
    var message_body   = form["body"].value;
    var message_addedTS = form["addedTS"].value;

    form.reset();

    $.log("clicked on message: id='" + message_id + "', body='" + message_body + "' for target=" + message_target);

    message_to_modify._id       = message_id;
    message_to_modify.message   = message_body;
    message_to_modify.target    = message_target;
    message_to_modify.addedTS   = message_addedTS;
    message_to_modify.deletedTS = new Date().getTime();

    message_to_delete._id       = message_id;
    message_to_delete._rev      = message_rev;

    function callback_error(stat, err, reason) {
	$.log("stat" + JSON.stringify(stat));
	$.log("err" + JSON.stringify(err));
	$.log("reason" + JSON.stringify(reason));
	alert("Error adding message to db: " + JSON.stringify(reason));
    }

    function callback_ok() {
	$.log("ok");
    }


    function mark_message_as_deleted() {
	$.log("deleting");

	//not working, using "removeDoc" instead
	app.db.saveDoc(message_to_modify, { success: callback_ok, error: callback_error });
    }

    function delete_message() {
	$.log("deleting");

	app.db.removeDoc(message_to_delete, { success: callback_ok, error: callback_error });
    }

    $(this).parents("li").hide('fast', delete_message());

    return false;

};
