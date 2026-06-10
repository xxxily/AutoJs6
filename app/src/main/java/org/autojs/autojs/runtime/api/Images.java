package org.autojs.autojs.runtime.api;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.Image;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.AutoJs;
import org.autojs.autojs.annotation.ScriptInterface;
import org.autojs.autojs.annotation.ScriptVariable;
import org.autojs.autojs.concurrent.VolatileDispose;
import org.autojs.autojs.core.image.ImageWrapper;
import org.autojs.autojs.core.image.RhinoColorFinder;
import org.autojs.autojs.core.image.Shootable;
import org.autojs.autojs.core.image.TemplateMatching;
import org.autojs.autojs.core.image.capture.ScreenCaptureRequester;
import org.autojs.autojs.core.image.capture.ScreenCapturer;
import org.autojs.autojs.core.image.capture.ScreenCapturerForegroundService;
import org.autojs.autojs.core.opencv.Mat;
import org.autojs.autojs.core.opencv.OpenCVHelper;
import org.autojs.autojs.core.pref.Language;
import org.autojs.autojs.core.pref.Pref;
import org.autojs.autojs.core.ui.inflater.util.Drawables;
import org.autojs.autojs.rhino.extension.AnyExtensions;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.runtime.api.ImageFeatureMatching.FeatureMatchingDescriptor;
import org.autojs.autojs.runtime.exception.WrappedRuntimeException;
import org.autojs.autojs.util.BitmapUtils;
import org.autojs.autojs6.R;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static android.app.Activity.RESULT_OK;
import static org.autojs.autojs.util.RhinoUtils.isMainThread;
import static org.autojs.autojs.util.StringUtils.str;

/**
 * Quick Reference – Image Size / Quality Helpers<br>
 * zh-CN: 快速参考 – 图像尺寸 / 质量相关辅助方法
 * <hr>
 * <b>scale</b><br>
 * Multiply both width and height of an <b>already-decoded</b> Bitmap by
 * the given scale factor(s).<br>
 * zh-CN: 在 <b>已解码</b> 的 Bitmap 上, 按照给定比例同时缩放宽高.
 * <p>
 * <b>resize</b><br>
 * Force an <b>already-decoded</b> Bitmap to the specified width/height.
 * Aspect ratio may be kept or ignored depending on the overload.<br>
 * zh-CN: 将 <b>已解码</b> Bitmap 调整为指定宽高; 可选择保持或忽略纵横比.
 * <p>
 * <b>downsample</b><br>
 * Shrink <b>before decoding</b> by setting <code>inSampleSize</code>; pixels are
 * skipped while reading, dramatically reducing memory.<br>
 * zh-CN: <b>解码前</b> 通过设置 <code>inSampleSize</code> 跳读像素, 显著降低解码分辨率与内存.
 * <p>
 * <b>compress</b><br>
 * Re-encode an existing Bitmap (or byte stream) into JPEG/PNG/WebP
 * with the given format & quality to reduce <b>disk</b> footprint.<br>
 * zh-CN: 使用指定格式与质量重新编码 Bitmap (或字节流), 以减小 <b>磁盘</b> 体积.
 * <p>
 * <b>save</b><br>
 * Convenience wrapper that internally invokes <code>compress</code> (with
 * default or user-supplied options) and then writes to storage.<br>
 * zh-CN: 便捷封装, 内部调用 <code>compress</code> (默认或自定义参数) 后写入存储.
 * <hr>
 * Created by Stardust on May 20, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 */
public class Images {

    private static final String TAG = Images.class.getSimpleName();
    private static volatile boolean sOpenCvInitialized;

    // Gate first captures/frames until this time.
    // zh-CN: 在该时间点之前, 首次截图/异步帧回调将被延迟或丢弃, 以避开授权弹窗渐隐动画.
    private volatile long mScreenCaptureReadyUptimeMillis = 0L;

    private final int mScreenCaptureRequestDelayMin;
    private final int mScreenCaptureRequestDelayMax;

    @ScriptVariable
    public final RhinoColorFinder colorFinder;

    private final Context mContext;
    private final ScriptRuntime mScriptRuntime;
    private final ScreenMetrics mScreenMetrics;
    private volatile ScreenCapturer.OnScreenCaptureAvailableListener mOnScreenCaptureAvailableListener;
    private Image mPreCapture;
    private ImageWrapper mPreCaptureImage;
    private ScreenCapturer mScreenCapturer;
    private ScreenCaptureRequester mScreenCaptureRequester;
    private final Set<ScreenCaptureSession> mScreenCaptureSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public Images(Context context, ScriptRuntime scriptRuntime) {
        mContext = context;
        mScreenCaptureRequestDelayMin = context.getResources().getInteger(R.integer.screen_capture_request_delay_min_value);
        mScreenCaptureRequestDelayMax = context.getResources().getInteger(R.integer.screen_capture_request_delay_max_value);
        mScreenMetrics = scriptRuntime.getScreenMetrics();
        mScriptRuntime = scriptRuntime;
        this.colorFinder = new RhinoColorFinder(mScreenMetrics);
    }

    public interface OnScreenCaptureAvailableListener {
        void onCaptureAvailable(@NonNull ImageWrapper imageWrapper);
    }

    @ScriptInterface
    public static void initOpenCvIfNeeded() {
        if (sOpenCvInitialized || OpenCVHelper.isInitialized()) {
            return;
        }
        AutoJs autoJs = AutoJs.getInstance();
        Activity activity = autoJs.getAppUtils().getCurrentActivity();
        Context context = activity == null ? autoJs.getApplication() : activity;
        Log.i(TAG, "OpenCV: initializing");
        if (isMainThread()) {
            OpenCVHelper.initIfNeeded(context, Images::initSuccessfully);
        } else {
            VolatileDispose<Boolean> result = new VolatileDispose<>();
            OpenCVHelper.initIfNeeded(context, () -> {
                initSuccessfully();
                result.setAndNotify(true);
            });
            if (Boolean.FALSE.equals(result.blockedGet(60_000))) {
                throw new RuntimeException("Timed out while initializing OpenCV");
            }
        }
    }

    private static void initSuccessfully() {
        sOpenCvInitialized = true;
        Log.i(TAG, "OpenCV: initialized");
    }

    public static int pixel(ImageWrapper image, int x, int y) {
        if (image == null) {
            throw new NullPointerException(str(R.string.error_method_called_with_null_argument, "Images.pixel", "image"));
        }
        try {
            return image.pixel(x, y);
        } finally {
            shoot(image);
        }
    }

    public static ImageWrapper concat(ScriptRuntime scriptRuntime, ImageWrapper imgA, ImageWrapper imgB, int direction) {
        try {
            return concatInternal(scriptRuntime, imgA, imgB, direction);
        } finally {
            shoot(imgA, imgB);
        }
    }

    private static ImageWrapper concatInternal(ScriptRuntime scriptRuntime, ImageWrapper imgA, ImageWrapper imgB, int direction) {
        if (!Arrays.asList(Gravity.START, Gravity.END, Gravity.TOP, Gravity.BOTTOM).contains(direction)) {
            throw new IllegalArgumentException(str(R.string.error_illegal_argument, "direction", direction));
        }
        int width;
        int height;
        if (direction == Gravity.START || direction == Gravity.TOP) {
            ImageWrapper tmp = imgA;
            imgA = imgB;
            imgB = tmp;
        }
        if (direction == Gravity.START || direction == Gravity.END) {
            width = imgA.getWidth() + imgB.getWidth();
            height = Math.max(imgA.getHeight(), imgB.getHeight());
        } else {
            width = Math.max(imgA.getWidth(), imgB.getWidth());
            height = imgA.getHeight() + imgB.getHeight();
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        if (direction == Gravity.START || direction == Gravity.END) {
            canvas.drawBitmap(imgA.getBitmap(), 0, (float) (height - imgA.getHeight()) / 2, paint);
            canvas.drawBitmap(imgB.getBitmap(), imgA.getWidth(), (float) (height - imgB.getHeight()) / 2, paint);
        } else {
            canvas.drawBitmap(imgA.getBitmap(), (float) (width - imgA.getWidth()) / 2, 0, paint);
            canvas.drawBitmap(imgB.getBitmap(), (float) (width - imgB.getWidth()) / 2, imgA.getHeight(), paint);
        }
        return ImageWrapper.ofBitmap(scriptRuntime, bitmap);
    }

    public static void saveBitmap(@NonNull Bitmap bitmap, String path) {
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(path));
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Bitmap scaleBitmap(Bitmap origin, int newWidth, int newHeight) {
        if (origin == null) {
            return null;
        }
        int height = origin.getHeight();
        int width = origin.getWidth();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        return Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
    }

    protected static void setImageCaptureCallback(ScriptRuntime scriptRuntime, OnScreenCaptureAvailableListener onScreenCaptureAvailableListener, Image image) {
        ImageWrapper ofImage = ImageWrapper.ofImage(scriptRuntime, image);
        onScreenCaptureAvailableListener.onCaptureAvailable(ofImage);
        // ofImage.recycle();
    }

    public ScriptPromiseAdapter requestScreenCapture(int orientation, int width, int height, boolean isAsync) {
        ScriptPromiseAdapter promiseAdapter = new ScriptPromiseAdapter();
        if (mScreenCapturer == null) {
            Handler handler = isAsync ? new Handler(Looper.getMainLooper()) : new Handler(mScriptRuntime.loopers.getServantLooper());
            Context contextForRequest = mScriptRuntime.app.getCurrentActivity();
            if (contextForRequest == null) contextForRequest = mContext;
            mScreenCaptureRequester = new ScreenCaptureRequester();
            mScreenCaptureRequester.request(contextForRequest, new ScreenCaptureRequester.Callback() {
                @Override
                public void onRequestResult(int resultCode, @Nullable Intent intent) {
                    if (resultCode == RESULT_OK && intent != null) {
                        try {
                            ScreenCapturer.Options options = new ScreenCapturer.Options(
                                    width, height, orientation, ScreenMetrics.getDeviceScreenDensity(), isAsync
                            );
                            mScreenCapturer = new ScreenCapturer(mContext, intent, options, handler);
                            mScreenCapturer.setImageCaptureCallback(mOnScreenCaptureAvailableListener);

                            int delayMs = Pref.getScreenCaptureRequestDelay();
                            if (delayMs < mScreenCaptureRequestDelayMin) delayMs = mScreenCaptureRequestDelayMin;
                            if (delayMs > mScreenCaptureRequestDelayMax) delayMs = mScreenCaptureRequestDelayMax;

                            mScreenCaptureReadyUptimeMillis = SystemClock.uptimeMillis() + delayMs;

                            // @Caution by JetBrains AI Assistant (GPT-5.2) on Jan 19, 2025.
                            //  ! Resolve immediately to avoid breaking ResultAdapter.wait semantics.
                            //  ! zh-CN: 必须立即 resolve, 避免破坏 ResultAdapter.wait 的语义/线程模型.
                            promiseAdapter.resolve(true);
                        } catch (SecurityException ex) {
                            promiseAdapter.reject(ex);
                        }
                    } else {
                        promiseAdapter.resolve(false);
                    }
                }

                @Override
                public void onRequestError(@NonNull Throwable t) {
                    promiseAdapter.reject(t);
                }
            });
        }
        return promiseAdapter;
    }

    @Nullable
    public ImageWrapper captureScreen() {
        synchronized (this) {
            if (mScreenCapturer == null) {
                throw new SecurityException(mContext.getString(R.string.error_no_screen_capture_permission));
            }

            // Delay first capture to skip the permission dialog fade-out animation.
            // zh-CN: 延迟首次取帧, 跳过授权弹窗渐隐动画.
            long waitMs = mScreenCaptureReadyUptimeMillis - SystemClock.uptimeMillis();
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Retry in Java side to avoid leaking transient null frames to JS.
            // zh-CN: 在 Java 层做重试, 避免把短暂的 null 帧暴露给 JS 层.
            Image capture = null;
            for (int i = 0; i < 6; i++) {
                capture = mScreenCapturer.capture();
                if (capture != null) break;
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Optional extra small backoff for switching moments.
            // zh-CN: 可选的额外小退避, 用于方向/应用切换瞬间.
            if (capture == null) {
                try {
                    Thread.sleep(60);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < 2; i++) {
                    capture = mScreenCapturer.capture();
                    if (capture != null) break;
                    try {
                        Thread.sleep(40);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            // If still no valid frame, fallback to previous cached image when possible.
            // zh-CN: 若仍无有效帧, 尽可能回退到上一帧缓存图像.
            if (capture == null) {
                if (mPreCaptureImage != null && !mPreCaptureImage.isRecycled()) {
                    // Return a clone to avoid user recycling the internal cache.
                    // zh-CN: 返回 clone, 避免用户 recycle() 影响内部缓存.
                    return mPreCaptureImage.clone();
                }
                return null;
            }

            // Recreate wrapper when image instance changed OR cached wrapper is missing OR cached wrapper was recycled.
            // zh-CN: 当 Image 实例发生变化/缓存包装对象为空/缓存包装对象已被回收时, 重新创建包装对象.
            if (capture != mPreCapture || mPreCaptureImage == null || mPreCaptureImage.isRecycled()) {
                mPreCapture = capture;
                if (mPreCaptureImage != null) {
                    mPreCaptureImage.recycle();
                }
                mPreCaptureImage = new ImageWrapper(mScriptRuntime, capture);
            }
            return mPreCaptureImage;
        }
    }

    public boolean captureScreen(String path) {
        ImageWrapper image = captureScreen();
        return image != null && image.saveTo(path);
    }

    public ScreenCaptureSession openCaptureSession(@NonNull CaptureSessionOptions options) {
        ScreenCaptureSession session = new ScreenCaptureSession(options);
        mScreenCaptureSessions.add(session);
        return session;
    }

    private void unregisterCaptureSession(@NonNull ScreenCaptureSession session) {
        mScreenCaptureSessions.remove(session);
    }

    private void closeCaptureSessions() {
        for (ScreenCaptureSession session : new ArrayList<>(mScreenCaptureSessions)) {
            session.closeFromOwner();
        }
        mScreenCaptureSessions.clear();
    }

    @NonNull
    private SessionCaptureResult captureScreenForSession(long timeoutMillis) {
        synchronized (this) {
            if (mScreenCapturer == null) {
                return SessionCaptureResult.failure(new ScreenCapturer.CaptureFailure(
                        ScreenCapturer.FAILURE_UNAUTHORIZED,
                        mContext.getString(R.string.error_no_screen_capture_permission),
                        "Call images.requestScreenCapture() before opening or using a capture session."
                ), 0L, null);
            }
            ScreenCapturer.CaptureResult result = mScreenCapturer.captureDetailed(timeoutMillis);
            if (result.image() == null) {
                ScreenCapturer.CaptureFailure failure = result.failure() != null
                        ? result.failure()
                        : new ScreenCapturer.CaptureFailure(
                        ScreenCapturer.FAILURE_NO_FRAME,
                        "No screen frame is available",
                        "Retry with a larger timeout or request screen capture permission again."
                );
                return SessionCaptureResult.failure(failure, result.durationMillis(), result.recoveryEvent());
            }
            return SessionCaptureResult.success(new ImageWrapper(mScriptRuntime, result.image()), result.durationMillis(), result.recoveryEvent());
        }
    }

    private record SessionCaptureResult(
            @Nullable ImageWrapper image,
            @Nullable ScreenCapturer.CaptureFailure failure,
            long durationMillis,
            @Nullable ScreenCapturer.RecoveryEvent recoveryEvent
    ) {
        static SessionCaptureResult success(
                @NonNull ImageWrapper image,
                long durationMillis,
                @Nullable ScreenCapturer.RecoveryEvent recoveryEvent
        ) {
            return new SessionCaptureResult(image, null, durationMillis, recoveryEvent);
        }

        static SessionCaptureResult failure(
                @NonNull ScreenCapturer.CaptureFailure failure,
                long durationMillis,
                @Nullable ScreenCapturer.RecoveryEvent recoveryEvent
        ) {
            return new SessionCaptureResult(null, failure, durationMillis, recoveryEvent);
        }

        boolean isSuccess() {
            return image != null;
        }
    }

    public static final class CaptureSessionOptions {

        public static final String PRESET_SINGLE = "single";
        public static final String PRESET_OCR = "ocr";
        public static final String PRESET_COLOR = "color";
        public static final String PRESET_LOW_POWER = "low_power";

        public final String preset;
        public final int cacheSize;
        public final long defaultTimeoutMillis;
        public final long minIntervalMillis;
        public final boolean logErrors;
        public final boolean autoRequest;

        public CaptureSessionOptions(
                @NonNull String preset,
                int cacheSize,
                long defaultTimeoutMillis,
                long minIntervalMillis,
                boolean logErrors,
                boolean autoRequest
        ) {
            this.preset = normalizePreset(preset);
            this.cacheSize = Math.max(1, cacheSize);
            this.defaultTimeoutMillis = Math.max(1L, defaultTimeoutMillis);
            this.minIntervalMillis = Math.max(0L, minIntervalMillis);
            this.logErrors = logErrors;
            this.autoRequest = autoRequest;
        }

        @NonNull
        public static CaptureSessionOptions forPreset(@Nullable String preset) {
            String normalized = normalizePreset(preset);
            return switch (normalized) {
                case PRESET_OCR -> new CaptureSessionOptions(PRESET_OCR, 2, 1800L, 250L, true, true);
                case PRESET_COLOR -> new CaptureSessionOptions(PRESET_COLOR, 2, 1000L, 16L, true, true);
                case PRESET_LOW_POWER -> new CaptureSessionOptions(PRESET_LOW_POWER, 1, 2500L, 1000L, true, true);
                default -> new CaptureSessionOptions(PRESET_SINGLE, 1, 1500L, 0L, true, true);
            };
        }

        @NonNull
        public static String normalizePreset(@Nullable String preset) {
            if (preset == null || preset.isBlank()) {
                return PRESET_SINGLE;
            }
            String key = preset.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            return switch (key) {
                case "continuous_ocr", "ocr_continuous" -> PRESET_OCR;
                case "find_color", "high_frequency_color", "high_frequency_find_color", "color_finder" -> PRESET_COLOR;
                case "lowpower", "background", "background_monitor", "monitor" -> PRESET_LOW_POWER;
                case PRESET_OCR, PRESET_COLOR, PRESET_LOW_POWER -> key;
                default -> PRESET_SINGLE;
            };
        }

        @NonNull
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("preset", preset);
            map.put("cacheSize", cacheSize);
            map.put("defaultTimeout", defaultTimeoutMillis);
            map.put("minInterval", minIntervalMillis);
            map.put("logErrors", logErrors);
            map.put("autoRequest", autoRequest);
            return map;
        }
    }

    public final class ScreenCaptureSession implements AutoCloseable {

        private final CaptureSessionOptions options;
        private final ArrayDeque<ImageWrapper> frameCache = new ArrayDeque<>();
        private final long openedUptimeMillis = SystemClock.uptimeMillis();
        private long lastCaptureUptimeMillis = 0L;
        private long attempts = 0L;
        private long frames = 0L;
        private long failures = 0L;
        private long timeouts = 0L;
        private long totalFrameDurationMillis = 0L;
        private long firstFrameMillis = -1L;
        private long lastFrameDurationMillis = -1L;
        private long orientationRecoveryCount = 0L;
        private long totalOrientationRecoveryMillis = 0L;
        private Map<String, Object> lastError;
        private Map<String, Object> lastRecovery;
        private boolean closed = false;

        private ScreenCaptureSession(@NonNull CaptureSessionOptions options) {
            this.options = options;
        }

        @Nullable
        public synchronized ImageWrapper latest() {
            ensureOpen();
            ImageWrapper image = frameCache.peekLast();
            if (image == null || image.isRecycled()) {
                return null;
            }
            return image.clone();
        }

        @Nullable
        public synchronized ImageWrapper nextFrame(long timeoutMillis) {
            ensureOpen();
            waitForMinInterval();

            long timeout = timeoutMillis > 0 ? timeoutMillis : options.defaultTimeoutMillis;
            attempts++;
            SessionCaptureResult result = captureScreenForSession(timeout);
            if (result.recoveryEvent() != null) {
                recordRecovery(result.recoveryEvent());
            }
            if (!result.isSuccess()) {
                recordFailure(result.failure(), result.durationMillis(), timeout);
                return null;
            }

            ImageWrapper image = result.image();
            if (image == null) {
                recordFailure(new ScreenCapturer.CaptureFailure(
                        ScreenCapturer.FAILURE_NO_FRAME,
                        "Capture returned no frame",
                        "Retry with a larger timeout or request screen capture permission again."
                ), result.durationMillis(), timeout);
                return null;
            }

            frames++;
            totalFrameDurationMillis += result.durationMillis();
            lastFrameDurationMillis = result.durationMillis();
            if (firstFrameMillis < 0L) {
                firstFrameMillis = SystemClock.uptimeMillis() - openedUptimeMillis;
            }
            lastCaptureUptimeMillis = SystemClock.uptimeMillis();
            lastError = null;

            ImageWrapper exposed;
            try {
                exposed = image.clone();
            } catch (RuntimeException ex) {
                image.recycle();
                throw ex;
            }
            addToCache(image);
            return exposed;
        }

        @NonNull
        public synchronized Map<String, Object> metrics() {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("preset", options.preset);
            metrics.put("attempts", attempts);
            metrics.put("frames", frames);
            metrics.put("failures", failures);
            metrics.put("timeouts", timeouts);
            metrics.put("timeoutRate", attempts == 0L ? 0.0 : (double) timeouts / (double) attempts);
            metrics.put("firstFrameMillis", firstFrameMillis);
            metrics.put("averageFrameMillis", frames == 0L ? -1.0 : (double) totalFrameDurationMillis / (double) frames);
            metrics.put("lastFrameMillis", lastFrameDurationMillis);
            metrics.put("orientationRecoveryCount", orientationRecoveryCount);
            metrics.put("averageOrientationRecoveryMillis", orientationRecoveryCount == 0L ? -1.0 : (double) totalOrientationRecoveryMillis / (double) orientationRecoveryCount);
            metrics.put("cachedFrames", frameCache.size());
            metrics.put("cacheSize", options.cacheSize);
            metrics.put("cachedFrameBytes", estimateCachedFrameBytes());
            metrics.put("usedMemoryBytes", usedMemoryBytes());
            metrics.put("closed", closed);
            metrics.put("lastError", lastError);
            metrics.put("lastRecovery", lastRecovery);
            metrics.put("options", options.toMap());
            return metrics;
        }

        @Nullable
        public synchronized Map<String, Object> lastError() {
            return lastError == null ? null : new LinkedHashMap<>(lastError);
        }

        public synchronized boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closeInternal(true);
        }

        private synchronized void closeFromOwner() {
            closeInternal(false);
        }

        private void closeInternal(boolean unregister) {
            if (closed) {
                return;
            }
            closed = true;
            while (!frameCache.isEmpty()) {
                ImageWrapper image = frameCache.removeFirst();
                if (image != null && !image.isRecycled()) {
                    image.recycle();
                }
            }
            if (unregister) {
                unregisterCaptureSession(this);
            }
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Screen capture session has been closed");
            }
        }

        private void waitForMinInterval() {
            if (options.minIntervalMillis <= 0L || lastCaptureUptimeMillis <= 0L) {
                return;
            }
            long waitMillis = lastCaptureUptimeMillis + options.minIntervalMillis - SystemClock.uptimeMillis();
            if (waitMillis <= 0L) {
                return;
            }
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void addToCache(@NonNull ImageWrapper image) {
            frameCache.addLast(image);
            while (frameCache.size() > options.cacheSize) {
                ImageWrapper old = frameCache.removeFirst();
                if (old != null && !old.isRecycled()) {
                    old.recycle();
                }
            }
        }

        private void recordFailure(@Nullable ScreenCapturer.CaptureFailure failure, long durationMillis, long timeoutMillis) {
            failures++;
            ScreenCapturer.CaptureFailure safeFailure = failure != null
                    ? failure
                    : new ScreenCapturer.CaptureFailure(
                    ScreenCapturer.FAILURE_NO_FRAME,
                    "Capture failed without detailed cause",
                    "Retry or request screen capture permission again."
            );
            if (ScreenCapturer.FAILURE_NO_FRAME.equals(safeFailure.category()) || durationMillis >= timeoutMillis) {
                timeouts++;
            }
            lastError = failureToMap(safeFailure, durationMillis);
            if (options.logErrors) {
                logCaptureFailure(safeFailure, durationMillis);
            }
        }

        private void recordRecovery(@NonNull ScreenCapturer.RecoveryEvent event) {
            orientationRecoveryCount++;
            totalOrientationRecoveryMillis += event.durationMillis();
            lastRecovery = recoveryToMap(event);
            String message = MessageFormat.format(
                    "[images.captureSession] recovered reason={0}, orientation={1}, size={2}x{3}, duration={4}ms",
                    event.reason(),
                    event.orientation(),
                    event.width(),
                    event.height(),
                    event.durationMillis()
            );
            Log.i(TAG, message);
            mScriptRuntime.console.info(message);
        }

        private void logCaptureFailure(@NonNull ScreenCapturer.CaptureFailure failure, long durationMillis) {
            String message = MessageFormat.format(
                    "[images.captureSession] capture failed category={0}, duration={1}ms, message={2}, suggestion={3}",
                    failure.category(),
                    durationMillis,
                    failure.message(),
                    failure.suggestion()
            );
            Log.w(TAG, message);
            mScriptRuntime.console.warn(message);
        }

        @NonNull
        private Map<String, Object> failureToMap(@NonNull ScreenCapturer.CaptureFailure failure, long durationMillis) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("category", failure.category());
            map.put("message", failure.message());
            map.put("suggestion", failure.suggestion());
            map.put("duration", durationMillis);
            map.put("at", System.currentTimeMillis());
            return map;
        }

        @NonNull
        private Map<String, Object> recoveryToMap(@NonNull ScreenCapturer.RecoveryEvent event) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("reason", event.reason());
            map.put("orientation", event.orientation());
            map.put("width", event.width());
            map.put("height", event.height());
            map.put("duration", event.durationMillis());
            map.put("uptime", event.uptimeMillis());
            return map;
        }

        private long estimateCachedFrameBytes() {
            long bytes = 0L;
            for (ImageWrapper image : frameCache) {
                if (image != null && !image.isRecycled()) {
                    bytes += (long) image.getWidth() * (long) image.getHeight() * 4L;
                }
            }
            return bytes;
        }

        private long usedMemoryBytes() {
            Runtime runtime = Runtime.getRuntime();
            return runtime.totalMemory() - runtime.freeMemory();
        }
    }

    public ImageWrapper copy(@NonNull ImageWrapper image) {
        try {
            return image.clone();
        } finally {
            shoot(image);
        }
    }

    public boolean save(@NonNull ImageWrapper image, @NonNull String path, @NonNull String format, int quality) throws IOException {
        try {
            var nicePath = AnyExtensions.toRuntimePath(path, mScriptRuntime, true);
            var file = new File(nicePath);
            File parentFile = file.getParentFile();
            if (parentFile != null && !parentFile.exists()) {
                if (!parentFile.mkdirs()) {
                    throw new IOException("Failed to create parent directory for image save: " + parentFile.getAbsolutePath());
                }
            }
            return saveInternal(image, nicePath, format, quality);
        } finally {
            shoot(image);
        }
    }

    private boolean saveInternal(@NonNull ImageWrapper image, @NonNull String path, @NonNull String format, int quality) throws IOException {
        Bitmap bitmap = image.getBitmap();
        Bitmap.CompressFormat compressFormat = parseImageFormat(format);

        if (compressFormat == Bitmap.CompressFormat.PNG && quality != 100) {
            byte[] compressed = PngQuantBridge.quantize(bitmap, quality);
            if (compressed != null) {
                try (FileOutputStream fos = new FileOutputStream(path)) {
                    fos.write(compressed);
                    return true;
                }
            }
            throw new WrappedRuntimeException("PNG quantization failed");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (compressFormat == Bitmap.CompressFormat.WEBP_LOSSLESS && quality != 100) {
                throw new IllegalArgumentException(mContext.getString(R.string.error_webp_lossless_quality_not_supported));
            }
        }

        try (FileOutputStream fos = new FileOutputStream(path)) {
            return bitmap.compress(compressFormat, quality, fos);
        }
    }

    public byte[] compressToBytes(@NotNull ImageWrapper image, @NotNull String format, int quality) {
        try {
            return compressToBytesInternal(image, format, quality);
        } finally {
            shoot(image);
        }
    }

    private byte[] compressToBytesInternal(@NotNull ImageWrapper image, @NotNull String format, int quality) {
        Bitmap bitmap = image.getBitmap();
        Bitmap.CompressFormat compressFormat = parseImageFormat(format);

        if (compressFormat == Bitmap.CompressFormat.PNG && quality != 100) {
            byte[] compressed = PngQuantBridge.quantize(bitmap, quality);
            if (compressed != null) {
                return compressed;
            }
            throw new WrappedRuntimeException("PNG quantization failed");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (compressFormat == Bitmap.CompressFormat.WEBP_LOSSLESS && quality != 100) {
                throw new IllegalArgumentException(mContext.getString(R.string.error_webp_lossless_quality_not_supported));
            }
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(compressFormat, quality, outputStream);
        return outputStream.toByteArray();
    }

    public ImageWrapper compress(@NotNull ImageWrapper image, @NotNull String format, int quality) throws BitmapUtils.DecodeException {
        return fromBytes(compressToBytes(image, format, quality));
    }

    // public EventEmitter select() {
    //     EventEmitter eventEmitter = new EventEmitter(scriptRuntime.bridges, scriptRuntime.timers.getTimerForCurrentThread());
    //     StartForResultActivity.start(mContext, new SelectImageCallback(this, eventEmitter, mContext, mContext.getString(R.string.text_select_image)));
    //     return eventEmitter;
    // }

    public void stopScreenCapture() {
        releaseScreenCapturer();
    }

    public ImageWrapper rotate(@NonNull ImageWrapper image, float x, float y, float degree) {
        try {
            return rotateInternal(image, x, y, degree);
        } finally {
            shoot(image);
        }
    }

    private ImageWrapper rotateInternal(@NonNull ImageWrapper image, float x, float y, float degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree, x, y);
        return ImageWrapper.ofBitmap(mScriptRuntime, Bitmap.createBitmap(image.getBitmap(), 0, 0, image.getWidth(), image.getHeight(), matrix, true));
    }

    @ScriptInterface
    public ImageWrapper flip(@NonNull ImageWrapper image, boolean horizontal, boolean vertical) {
        try {
            return flipInternal(image, horizontal, vertical);
        } finally {
            shoot(image);
        }
    }

    private ImageWrapper flipInternal(@NonNull ImageWrapper image, boolean horizontal, boolean vertical) {
        Bitmap original = image.getBitmap();
        Matrix matrix = new Matrix();

        // Set scaling ratio according to input parameters.
        // zh-CN: 根据传入参数设置缩放比例.
        float sx = horizontal ? -1.0f : 1.0f;
        float sy = vertical ? -1.0f : 1.0f;
        matrix.preScale(sx, sy);

        // After horizontal flip, translate to image width position.
        // zh-CN: 水平方向翻转后, 平移到图像宽度的位置.
        if (horizontal) matrix.postTranslate(original.getWidth(), 0);

        // After vertical flip, translate to image height position.
        // zh-CN: 垂直方向翻转后, 平移到图像高度的位置.
        if (vertical) matrix.postTranslate(0, original.getHeight());

        Bitmap flipped = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), matrix, true);
        return ImageWrapper.ofBitmap(mScriptRuntime, flipped);
    }

    public ImageWrapper clip(@NonNull ImageWrapper image, int x, int y, int w, int h) {
        try {
            return clipInternal(image, x, y, w, h);
        } finally {
            shoot(image);
        }
    }

    private ImageWrapper clipInternal(@NonNull ImageWrapper image, int x, int y, int w, int h) {
        return ImageWrapper.ofBitmap(mScriptRuntime, Bitmap.createBitmap(image.getBitmap(), x, y, w, h));
    }

    public ImageWrapper read(String path) {
        return read(path, false);
    }

    @Nullable
    public ImageWrapper read(String path, boolean isStrict) {
        var fullPath = mScriptRuntime.files.path(path);
        Bitmap bitmap = BitmapFactory.decodeFile(fullPath);
        if (bitmap == null) {
            if (!isStrict) return null;
            throw new RuntimeException(mContext.getString(R.string.error_file_in_path_does_not_exist, fullPath));
        }
        return ImageWrapper.ofBitmap(mScriptRuntime, bitmap);
    }

    @NotNull
    @Contract("_ -> new")
    public static Mat imread(String path) {
        initOpenCvIfNeeded();
        return new Mat(Imgcodecs.imread(path).nativeObj);
    }

    public ImageWrapper fromBase64(String data) {
        return ImageWrapper.ofBitmap(mScriptRuntime, Drawables.loadBase64Data(data));
    }

    public String toBase64(ImageWrapper image, String format, int quality) {
        try {
            byte[] input = toBytes(image, format, quality);
            return Base64.encodeToString(input, Base64.NO_WRAP);
        } finally {
            shoot(image);
        }
    }

    public byte[] toBytes(@NonNull ImageWrapper image, String format, int quality) {
        try {
            Bitmap.CompressFormat compressFormat = parseImageFormat(format);
            Bitmap bitmap = image.getBitmap();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(compressFormat, quality, outputStream);
            return outputStream.toByteArray();
        } finally {
            shoot(image);
        }
    }

    public ImageWrapper fromBytes(byte[] bytes) throws BitmapUtils.DecodeException {
        return ImageWrapper.ofBitmap(mScriptRuntime, BitmapUtils.bitmapFromByteArrayOrThrow(bytes));
    }

    /** @noinspection deprecation */
    private Bitmap.CompressFormat parseImageFormat(String format) {
        return switch (format.toLowerCase(Language.getPrefLanguage().getLocale())) {
            case "png" -> Bitmap.CompressFormat.PNG;
            case "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG;
            case "webp" -> Bitmap.CompressFormat.WEBP;
            case "webp_lossy", "webp-lossy" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    yield Bitmap.CompressFormat.WEBP_LOSSY;
                }
                throw new IllegalArgumentException("Image format \"WEBP_LOSSY\" only supports on Android API Level 30 (11) [R] and above");
            }
            case "webp_lossless", "webp-lossless" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    yield Bitmap.CompressFormat.WEBP_LOSSLESS;
                }
                throw new IllegalArgumentException("Image format \"WEBP_LOSSLESS\" only supports on Android API Level 30 (11) [R] and above");
            }
            default -> throw new IllegalArgumentException(str(R.string.error_illegal_argument, "format", format));
        };
    }

    public ImageWrapper load(String src) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(src).openConnection();
        connection.setDoInput(true);
        connection.connect();
        return ImageWrapper.ofBitmap(mScriptRuntime, BitmapFactory.decodeStream(connection.getInputStream()));
    }

    public ImageWrapper invert(@NonNull ImageWrapper image) {
        try {
            return invertInternal(image);
        } finally {
            shoot(image);
        }
    }

    private ImageWrapper invertInternal(@NonNull ImageWrapper image) {
        initOpenCvIfNeeded();

        Bitmap originalBitmap = image.getBitmap();
        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();

        org.opencv.core.Mat srcMat = new org.opencv.core.Mat();
        Utils.bitmapToMat(originalBitmap, srcMat);

        // 分离原始 Mat 的通道
        List<org.opencv.core.Mat> channels = new ArrayList<>(4);
        Core.split(srcMat, channels);
        org.opencv.core.Mat alphaChannel = channels.get(3); // 提取 alpha 通道

        // 对 BGR 通道进行反色操作
        org.opencv.core.Mat bgrMat = new org.opencv.core.Mat();
        Imgproc.cvtColor(srcMat, bgrMat, Imgproc.COLOR_BGRA2BGR);
        org.opencv.core.Mat invertedBGRMat = new org.opencv.core.Mat();
        Core.bitwise_not(bgrMat, invertedBGRMat);

        // 合并反色后的 BGR 通道与原始的 alpha 通道
        channels.set(0, new org.opencv.core.Mat());
        channels.set(1, new org.opencv.core.Mat());
        channels.set(2, new org.opencv.core.Mat());
        channels.subList(0, 3).clear(); // 清除前 3 个通道并加入反色后的 BGR 通道
        Core.split(invertedBGRMat, channels); // 拆分并自动添加到 channels 的前 3 个位置
        channels.add(3, alphaChannel); // 将原来的 alpha 通道放回最后一个位置

        org.opencv.core.Mat destMat = new org.opencv.core.Mat();
        Core.merge(channels, destMat); // 合并 4 个通道 (BGR + 原始 alpha)

        // 转换反色后的 Mat 回 Bitmap
        Bitmap invertedBitmap = Bitmap.createBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(destMat, invertedBitmap);

        return ImageWrapper.ofBitmap(mScriptRuntime, invertedBitmap);
    }

    public void setImageCaptureCallback(OnScreenCaptureAvailableListener onScreenCaptureAvailableListener) {
        mOnScreenCaptureAvailableListener = new ScreenCaptureAvailableHandler(mScriptRuntime, imageWrapper -> {
            // Drop early frames before ready time.
            // zh-CN: ready 时间点之前的帧直接丢弃, 避免把授权弹窗渐隐截入异步回调.
            if (SystemClock.uptimeMillis() < mScreenCaptureReadyUptimeMillis) return;
            onScreenCaptureAvailableListener.onCaptureAvailable(imageWrapper);
        });
        if (mScreenCapturer != null) {
            mScreenCapturer.setImageCaptureCallback(mOnScreenCaptureAvailableListener);
        }
    }

    public void releaseScreenCapturer() {
        closeCaptureSessions();
        synchronized (this) {
            if (mScreenCapturer != null) {
                mScreenCapturer.release();
                mScreenCapturer = null;
            }
            // Reset gate.
            // zh-CN: 重置延迟门闩.
            mScreenCaptureReadyUptimeMillis = 0L;

            if (mPreCapture != null) {
                mPreCapture.close();
                mPreCapture = null;
            }
            if (mPreCaptureImage != null) {
                mPreCaptureImage.recycle();
                mPreCaptureImage = null;
            }
            releaseScreenCaptureRequester();
        }
    }

    public void stopScreenCapturerForegroundService() {
        var applicationContext = AutoJs.getInstance().getApplication().getApplicationContext();
        applicationContext.stopService(new Intent(applicationContext, ScreenCapturerForegroundService.class));
        releaseScreenCaptureRequester();
    }

    private void releaseScreenCaptureRequester() {
        if (mScreenCaptureRequester != null) {
            mScreenCaptureRequester.unbindService();
            mScreenCaptureRequester = null;
        }
    }

    @ScriptInterface
    public Point findImage(ImageWrapper image, ImageWrapper template) throws Exception {
        return findImage(image, template, 0.9f, null);
    }

    @ScriptInterface
    public Point findImage(ImageWrapper image, ImageWrapper template, float threshold) throws Exception {
        return findImage(image, template, threshold, null);
    }

    @ScriptInterface
    public Point findImage(ImageWrapper image, ImageWrapper template, float threshold, Rect rect) throws Exception {
        return findImage(image, template, 0.7f, threshold, rect, TemplateMatching.MAX_LEVEL_AUTO);
    }

    @Nullable
    @ScriptInterface
    public Point findImage(ImageWrapper image, ImageWrapper template, float weakThreshold, float strictThreshold, Rect rect, int maxLevel) throws Exception {
        try {
            return findImageInternal(image, template, weakThreshold, strictThreshold, rect, maxLevel);
        } finally {
            shoot(image, template);
        }
    }

    private Point findImageInternal(ImageWrapper image, ImageWrapper template, float weakThreshold, float strictThreshold, Rect rect, int maxLevel) throws Exception {
        initOpenCvIfNeeded();
        if (image == null) {
            throw new NullPointerException(mContext.getString(R.string.error_method_called_with_null_argument, "Images.findImage", "image"));
        }
        if (template == null) {
            throw new NullPointerException(mContext.getString(R.string.error_method_called_with_null_argument, "Images.findImage", "template"));
        }
        Mat src = image.getMat();
        if (rect != null) {
            if (template.getWidth() > rect.width) {
                throw new Exception(mContext.getString(R.string.error_excessive_width_for_template_n_region, template.getWidth(), rect.width));
            }
            if (template.getHeight() > rect.height) {
                throw new Exception(mContext.getString(R.string.error_excessive_height_for_template_n_region, template.getHeight(), rect.height));
            }
            src = new Mat(src, rect);
        }
        @Nullable
        org.opencv.core.Point point;
        try {
            point = TemplateMatching.singleTemplateMatching(
                    src,
                    template.getMat(),
                    new TemplateMatching.Options(TemplateMatching.MATCHING_METHOD_NONE, weakThreshold, strictThreshold, maxLevel)
            );
        } finally {
            if (src != image.getMat()) {
                OpenCVHelper.release(src);
            }
        }
        if (point != null) {
            if (rect != null) {
                point.x += rect.x;
                point.y += rect.y;
            }
            point.x = mScreenMetrics.scaleX((int) point.x);
            point.y = mScreenMetrics.scaleX((int) point.y);
        }
        return point;
    }

    public List<TemplateMatching.Match> matchTemplate(ImageWrapper image, ImageWrapper template, float weakThreshold, float strictThreshold, Rect rect, int maxLevel, int limit, boolean useTransparentMask) {
        try {
            return matchTemplateInternal(image, template, weakThreshold, strictThreshold, rect, maxLevel, limit, useTransparentMask);
        } finally {
            shoot(image, template);
        }
    }

    private List<TemplateMatching.Match> matchTemplateInternal(ImageWrapper image, ImageWrapper template, float weakThreshold, float strictThreshold, Rect rect, int maxLevel, int limit, boolean useTransparentMask) {
        initOpenCvIfNeeded();
        if (image == null) {
            throw new NullPointerException(mContext.getString(R.string.error_method_called_with_null_argument, "Images.matchTemplate", "image"));
        }
        if (template == null) {
            throw new NullPointerException(mContext.getString(R.string.error_method_called_with_null_argument, "Images.matchTemplate", "template"));
        }
        Mat src = image.getMat();
        if (rect != null) {
            src = new Mat(src, rect);
        }
        List<TemplateMatching.Match> result = TemplateMatching.fastTemplateMatching(
                src,
                template.getMat(),
                new TemplateMatching.Options(-1, weakThreshold, strictThreshold, maxLevel, useTransparentMask, limit)
        );

        if (src != image.getMat()) {
            OpenCVHelper.release(src);
        }

        for (TemplateMatching.Match match : result) {
            Point point = match.point;
            if (rect != null) {
                point.x += rect.x;
                point.y += rect.y;
            }
            point.x = mScreenMetrics.scaleX((int) point.x);
            point.y = mScreenMetrics.scaleX((int) point.y);
        }
        return result;
    }

    public Mat newMat() {
        return new Mat();
    }

    public Mat newMat(Mat mat, Rect rect) {
        return new Mat(mat, rect);
    }

    @ScriptInterface
    public FeatureMatchingDescriptor detectAndComputeFeatures(Mat mat, float scale, int cvtColor, int method) {
        return ImageFeatureMatching.createFeatureMatchingDescriptor(mat, cvtColor, scale, method);
    }

    public ScreenCapturer getScreenCapturer() {
        return mScreenCapturer;
    }

    @Nullable
    public ScreenCapturer.Options getScreenCaptureOptions() {
        synchronized (this) {
            return mScreenCapturer != null ? mScreenCapturer.getOptions() : null;
        }
    }

    public static void shoot(Shootable<?>... shootableArgs) {
        Arrays.stream(shootableArgs).filter(Objects::nonNull).forEach(Shootable::shoot);
    }

}
