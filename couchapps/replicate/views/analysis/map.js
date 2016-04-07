function (doc) {
  if (doc.type == "problem")
    emit(doc._id, doc._rev)
}
