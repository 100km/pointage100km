
function couchapp_load(scripts) {
  for (var i=0; i < scripts.length; i++) {
    document.write('<script src="'+scripts[i]+'"><\/script>');
  }
}


couchapp_load(([
  "../common/lib/sha1.js",
  "../common/lib/json2.js",
]).concat(
  (typeof (window.jQuery) == 'undefined' ) ?
  [ "../common/lib/jquery.js" ] : [ ]
).concat([
  "../common/lib/db_api/app-info.js",
  "../common/lib/db_api/checkpoints.js",
  "../common/lib/db_api/global.js",
  "../common/lib/db_api/ids.js",
  "../common/lib/db_api/messages.js",
  "../common/lib/db_api/previous.js",
  "../common/lib/db_api/recent.js",
  "../common/lib/db_api/search.js",
  "../common/lib/db_api/teams.js",
  "../common/lib/utils.js",
  "../common/lib/uuid.js",
  "../common/lib/underscore-min.js",
  "../common/lib/jquery.couch.js",
  "../common/vendor/couchapp/jquery.couch.app.js",
  "../common/vendor/couchapp/jquery.couch.app.util.js",
  "../common/vendor/couchapp/jquery.mustache.js",
  "../common/vendor/couchapp/jquery.evently.js"
]));
