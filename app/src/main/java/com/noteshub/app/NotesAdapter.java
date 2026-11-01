package com.noteshub.app;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    public interface OnNoteActionListener {
        void onView(Note note);
        void onDownload(Note note);
        void onShare(Note note);
        void onEdit(Note note);
        void onDelete(Note note);
    }

    private final List<Note> allNotes = new ArrayList<>();
    private final List<Note> visibleNotes = new ArrayList<>();
    private final String currentUserId;
    private final OnNoteActionListener listener;

    private String query = "";
    private boolean onlyMine = false;
    private String semesterFilter = null; // null = all semesters
    private String subjectFilter = null;  // null = all subjects

    public NotesAdapter(String currentUserId, OnNoteActionListener listener) {
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    public void setNotes(List<Note> notes) {
        allNotes.clear();
        allNotes.addAll(notes);
        applyFilter();
    }

    public void setQuery(String q) {
        this.query = q == null ? "" : q.trim().toLowerCase(Locale.getDefault());
        applyFilter();
    }

    public void setOnlyMine(boolean onlyMine) {
        this.onlyMine = onlyMine;
        applyFilter();
    }

    public void setSemesterFilter(String semester) {
        this.semesterFilter = semester;
        applyFilter();
    }

    public void setSubjectFilter(String subject) {
        this.subjectFilter = subject;
        applyFilter();
    }

    /** Combines text search, "my uploads", semester and subject filters. */
    private void applyFilter() {
        visibleNotes.clear();
        for (Note n : allNotes) {
            if (onlyMine && !currentUserId.equals(n.getUploaderId())) continue;
            if (semesterFilter != null && !semesterFilter.equalsIgnoreCase(n.getSemester())) continue;
            if (subjectFilter != null && !subjectFilter.equalsIgnoreCase(n.getSubject())) continue;
            if (query.isEmpty() || contains(n.getTitle()) || contains(n.getSubject())
                    || contains(n.getSemester()) || contains(n.getUploaderName())) {
                visibleNotes.add(n);
            }
        }
        notifyDataSetChanged();
    }

    private boolean contains(String field) {
        return field != null && field.toLowerCase(Locale.getDefault()).contains(query);
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder h, int position) {
        Note note = visibleNotes.get(position);
        h.tvTitle.setText(note.getTitle());
        h.tvSubject.setText(note.getSubject() + "  •  " + note.getSemester());

        boolean hasDesc = note.getDescription() != null && !note.getDescription().isEmpty();
        h.tvDescription.setText(note.getDescription());
        h.tvDescription.setVisibility(hasDesc ? View.VISIBLE : View.GONE);

        String date = DateFormat.format("dd MMM yyyy", note.getTimestamp()).toString();
        h.tvUploader.setText("Uploaded by " + note.getUploaderName() + "  •  " + date);

        h.itemView.setOnClickListener(v -> listener.onView(note));
        h.btnView.setOnClickListener(v -> listener.onView(note));
        h.btnDownload.setOnClickListener(v -> listener.onDownload(note));
        h.btnShare.setOnClickListener(v -> listener.onShare(note));

        boolean isOwner = currentUserId.equals(note.getUploaderId());
        h.btnEdit.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        h.btnDelete.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        h.btnEdit.setOnClickListener(v -> listener.onEdit(note));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(note));
    }

    @Override
    public int getItemCount() { return visibleNotes.size(); }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSubject, tvDescription, tvUploader;
        Button btnView, btnDownload, btnShare, btnEdit, btnDelete;

        NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSubject = itemView.findViewById(R.id.tvSubject);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvUploader = itemView.findViewById(R.id.tvUploader);
            btnView = itemView.findViewById(R.id.btnView);
            btnDownload = itemView.findViewById(R.id.btnDownload);
            btnShare = itemView.findViewById(R.id.btnShare);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
