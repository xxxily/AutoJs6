package com.stardust.autojs.core.inputevent;

import android.content.Context;

import androidx.annotation.NonNull;

import org.autojs.autojs.core.record.inputevent.TouchCoordinateMapper;

import static com.stardust.autojs.core.record.inputevent.InputEventRecorder.parseDeviceNumber;


/**
 * Created by Stardust on 2017/7/20.
 */

public class TouchObserver implements InputEventObserver.InputEventListener {


    public interface OnTouchEventListener {
        void onTouch(int x, int y);

        default void onTouch(int x, int y, int rawX, int rawY) {
            onRawTouch(rawX, rawY);
            onTouch(x, y);
        }

        default void onRawTouch(int x, int y) {
            /* Empty by default. */
        }
    }

    private int mTouchX, mTouchY;
    private OnTouchEventListener mOnTouchEventListener;
    private int mLastTouchX = -1, mLastTouchY = -1;
    private InputEventObserver mInputEventObserver;
    private final TouchCoordinateMapper mTouchCoordinateMapper;

    public TouchObserver(InputEventObserver observer) {
        this(observer, null);
    }

    public TouchObserver(InputEventObserver observer, Context context) {
        mInputEventObserver = observer;
        mTouchCoordinateMapper = context == null ? null : new TouchCoordinateMapper(context.getApplicationContext());
    }

    public void observe() {
        mInputEventObserver.addListener(this);
    }

    public void stop() {
        mInputEventObserver.removeListener(this);
    }

    public void setOnTouchEventListener(OnTouchEventListener onTouchEventListener) {
        mOnTouchEventListener = onTouchEventListener;
    }

    private void onTouch(int x, int y) {
        mTouchX = x;
        mTouchY = y;
        if (mOnTouchEventListener != null) {
            mOnTouchEventListener.onTouch(mapX(x), mapY(y), x, y);
        }
    }


    @Override
    public void onInputEvent(@NonNull InputEventObserver.InputEvent event) {
        int device = parseDeviceNumber(event.device);
        int type = (int) Long.parseLong(event.type, 16);
        int code = (int) Long.parseLong(event.code, 16);
        int value = (int) Long.parseLong(event.value, 16);
        if (type != InputEventCodes.EV_ABS) {
            return;
        }
        if (code == InputEventCodes.ABS_MT_POSITION_X || code == InputEventCodes.ABS_X) {
            updateTouchDevice(device);
            onTouchX(value);
            return;
        }
        if (code == InputEventCodes.ABS_MT_POSITION_Y || code == InputEventCodes.ABS_Y) {
            updateTouchDevice(device);
            onTouchY(value);
            return;
        }
        if (mLastTouchX >= 0) {
            onTouch(mLastTouchX, mTouchY);
            mLastTouchX = -1;
            return;
        }
        if (mLastTouchY >= 0) {
            onTouch(mTouchX, mLastTouchY);
            mLastTouchY = -1;
        }
    }

    private void onTouchX(int value) {
        mLastTouchX = value;
    }

    private void onTouchY(int value) {
        if (mLastTouchX >= 0) {
            onTouch(mLastTouchX, value);
            return;
        }
        mLastTouchY = value;
    }

    private void updateTouchDevice(int device) {
        if (mTouchCoordinateMapper != null) {
            mTouchCoordinateMapper.updateTouchDevice(device);
        }
    }

    private int mapX(int rawValue) {
        return mTouchCoordinateMapper == null ? rawValue : mTouchCoordinateMapper.mapX(rawValue);
    }

    private int mapY(int rawValue) {
        return mTouchCoordinateMapper == null ? rawValue : mTouchCoordinateMapper.mapY(rawValue);
    }

}
