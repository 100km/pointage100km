function(doc) {
  if (doc.type == "contestant-checkpoint" &&
      doc.created_at) {
    emit(doc.created_at, doc);
  }
};
