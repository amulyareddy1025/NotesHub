package com.noteshub.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-app PDF viewer built on android.graphics.pdf.PdfRenderer (no extra libraries).
 * The PDF is downloaded once into the app cache, then pages are rendered on demand.
 */
public class PdfViewerActivity extends AppCompatActivity {

    public static final String EXTRA_STORAGE_PATH = "storage_path";
    public static final String EXTRA_FILE_NAME = "file_name";
    public static final String EXTRA_FILE_URL = "file_url";

    private RecyclerView recycler;
    private ProgressBar progress;
    private TextView tvStatus;
    private Button btnOpenExternal;

    private PdfRenderer renderer;
    private ParcelFileDescriptor pfd;
    private File cacheFile;
    private FileDownloadTask downloadTask;
    private String fileUrl;

    private final Object lock = new Object();
    private volatile boolean closed = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int pageWidthPx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        recycler = findViewById(R.id.recyclerPages);
        progress = findViewById(R.id.progressDownload);
        tvStatus = findViewById(R.id.tvStatus);
        btnOpenExternal = findViewById(R.id.btnOpenExternal);

        String localPath = getIntent().getStringExtra(EXTRA_STORAGE_PATH);

        String fileName = getIntent().getStringExtra(EXTRA_FILE_NAME);

        fileUrl = getIntent().getStringExtra(EXTRA_FILE_URL);

        setTitle(fileName == null ? "PDF" : fileName);

        pageWidthPx = getResources().getDisplayMetrics().widthPixels;

        btnOpenExternal.setVisibility(View.GONE);

        if (localPath == null || localPath.isEmpty()) {
            showError("PDF path not found.");
            return;
        }

        cacheFile = new File(localPath);

        if (cacheFile.exists() && cacheFile.isFile()) {
            openPdf();
        } else {
            showError("PDF file is not available on this device.");
        }
    }

    private void download(String storagePath) {
        if (!AppUtils.isOnline(this)) {
            showError("No internet connection.");
            return;
        }
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("Loading PDF...");
        progress.setVisibility(View.VISIBLE);

        final File part = new File(cacheFile.getPath() + ".part");
        downloadTask = FirebaseStorage.getInstance().getReference(storagePath).getFile(part);
        downloadTask
                .addOnProgressListener(s -> {
                    long total = Math.max(1, s.getTotalByteCount());
                    progress.setProgress((int) (100 * s.getBytesTransferred() / total));
                })
                .addOnSuccessListener(s -> {
                    if (closed) return;
                    if (part.renameTo(cacheFile)) {
                        openPdf();
                    } else {
                        showError("Could not store the PDF.");
                    }
                })
                .addOnFailureListener(e -> {
                    part.delete();
                    if (!closed) showError("Could not load the PDF. Check your connection.");
                });
    }

    private void openPdf() {
        try {
            pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            recycler.setLayoutManager(new LinearLayoutManager(this));
            recycler.setAdapter(new PageAdapter(renderer.getPageCount()));
            progress.setVisibility(View.GONE);
            tvStatus.setVisibility(View.GONE);
            btnOpenExternal.setVisibility(View.GONE);
        } catch (IOException | SecurityException e) {
            cacheFile.delete();
            showError("This PDF cannot be opened (it may be corrupted or password protected).");
        }
    }

    private void showError(String msg) {
        progress.setVisibility(View.GONE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(msg);
        btnOpenExternal.setVisibility(fileUrl == null ? View.GONE : View.VISIBLE);
    }

    private void openExternally() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open this link", Toast.LENGTH_SHORT).show();
        }
    }

    /** PdfRenderer allows only one open page at a time, so rendering is serialized. */
    private Bitmap renderPage(int index) {
        synchronized (lock) {
            if (closed || renderer == null) return null;
            try (PdfRenderer.Page page = renderer.openPage(index)) {
                int w = pageWidthPx;
                int h = (int) ((float) w * page.getHeight() / page.getWidth());
                Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.WHITE);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return bmp;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.PageHolder> {
        private final int count;
        PageAdapter(int count) { this.count = count; }

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pdf_page, parent, false);
            return new PageHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder h, int position) {
            h.image.setImageBitmap(null);
            h.image.setMinimumHeight((int) (pageWidthPx * 1.41f)); // placeholder (A4 ratio)
            h.boundPos = position;
            executor.execute(() -> {
                Bitmap bmp = renderPage(position);
                if (bmp == null) return;
                runOnUiThread(() -> {
                    if (!closed && h.boundPos == position) {
                        h.image.setMinimumHeight(0);
                        h.image.setImageBitmap(bmp);
                    }
                });
            });
        }

        @Override
        public void onViewRecycled(@NonNull PageHolder h) {
            h.boundPos = -1;
            h.image.setImageBitmap(null);
        }

        @Override
        public int getItemCount() { return count; }

        class PageHolder extends RecyclerView.ViewHolder {
            final ImageView image;
            int boundPos = -1;
            PageHolder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.imgPage);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        closed = true;
        executor.shutdownNow();
        synchronized (lock) {
            try {
                if (renderer != null) renderer.close();
                if (pfd != null) pfd.close();
            } catch (Exception ignored) { }
            renderer = null;
        }
        super.onDestroy();
    }
}
