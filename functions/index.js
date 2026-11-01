// Optional: sends a push notification to every device subscribed to the "new_notes" topic
// whenever a document is created in the "notes" collection.
// Requires the Firebase "Blaze" (pay-as-you-go) plan to deploy; usage for a class project is
// effectively free. The Android app works without this function - only notifications are lost.

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

exports.notifyNewNote = onDocumentCreated("notes/{noteId}", async (event) => {
  const note = event.data && event.data.data();
  if (!note) return;

  // Data-only message: the app builds the notification itself (and skips the uploader).
  await getMessaging().send({
    topic: "new_notes",
    data: {
      noteId: event.params.noteId,
      title: String(note.title || ""),
      subject: String(note.subject || ""),
      semester: String(note.semester || ""),
      uploaderId: String(note.uploaderId || ""),
      uploaderName: String(note.uploaderName || ""),
    },
    android: { priority: "high" },
  });
});
