// Check if this document participates to computing liveness information
function (doc, req) {
  // We cannot check the content of the document as we are also interested
  // in deleted document.
  return doc._id.indexOf("ping-") == 0 || doc._id.indexOf("checkpoints-") == 0;
}
