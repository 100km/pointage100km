function(doc, req) {
  return (doc._id == "touch_me" // TODO use a more explicit touch_me
       || doc._id == "infos");
}
