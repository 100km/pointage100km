function (doc) {
  if (doc.type == "analysis")
    emit(doc._id, doc._rev)
}
