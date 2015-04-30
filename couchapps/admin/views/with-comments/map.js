function(doc) {
  if (doc.comment && doc.type == "contestant")
    emit([doc.name, doc.first_name], doc);
}
