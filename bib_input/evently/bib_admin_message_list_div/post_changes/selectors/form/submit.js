function() {
    var app = $$(this).app;
    var form = $(this)[0];
    var message_to_modify = {};
    var message_to_delete = {};
    var message_id      = form["id"].value;
    var message_rev     = form["rev"].value;
    var message_target  = form["target"].value;
    var message_body    = form["body"].value;
    var message_addedTS = form["addedTS"].value;

    form.reset();

    message_target = parseInt(message_target, 10); //hackish, message_target changes type from string to int
    message_addedTS = parseInt(message_addedTS, 10); //hackish, message_addedTS changes type from string to int

    $.log("clicked on message: id='" + message_id + "', body='" + message_body + "' for target=" + message_target);

    //to use saveDoc and update an existing document, _id and _rev are required, as well as the rest of the fields (don't know if I could just omit those that do not need to be updated)
    message_to_modify._id       = message_id;
    message_to_modify._rev      = message_rev;
    message_to_modify.message   = message_body;
    if (!isNaN(message_target)) {
	message_to_modify.target    = message_target;
    }
    message_to_modify.addedTS   = message_addedTS;
    message_to_modify.deletedTS = new Date().getTime();

    //to use removeDoc, only _id and _rev are required
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
	$.log("marking as deleted");

	app.db.saveDoc(message_to_modify, { success: callback_ok, error: callback_error });
    }

    function delete_message() {
	$.log("deleting");

	app.db.removeDoc(message_to_delete, { success: callback_ok, error: callback_error });
    }

    $(this).parents("li").hide('fast', mark_message_as_deleted());

    return false;

};
