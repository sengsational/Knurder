package com.sengsational.sephiroth;

/**
 * Created by Owner on 4/8/2017.
 */

import android.content.Context;
import android.graphics.PointF;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * UILongPressGestureRecognizer looks for long-press gestures. The user must
 * press one or more fingers on a view and hold them there for a minimum period of time before the action triggers. While down,
 * the user’s fingers may not move more than a specified distance; if they move beyond the specified distance, the gesture fails.
 *
 * @author alessandro crugnola
 * @see <a href='https://developer.apple.com/reference/uikit/uilongpressgesturerecognizer'>
 * https://developer.apple.com/reference/uikit/uilongpressgesturerecognizer</a>
 */
@SuppressWarnings ("unused")
public class UILongPressGestureRecognizer extends UIGestureRecognizer implements UIContinuousRecognizer {
    private static final String TAG = UILongPressGestureRecognizer.class.getSimpleName();

    private long longPressTimeOut = TAP_TIMEOUT + LONG_PRESS_TIMEOUT;

    // request to change the current state to Failed
    public static final int MESSAGE_FAILED = 1;
    // request to change the current state to Possible
    private static final int MESSAGE_RESET = 2;
    // we handle the action_pointer_up received in the onTouchEvent with a delay
    // in order to check how many fingers were actually down when we're checking them
    // in the action_up.
    private static final int MESSAGE_POINTER_UP = 3;
    // post handle the long press event
    private static final int MESSAGE_LONG_PRESS = 4;

    // number of required fingers (default is 1)
    private int mTouchesRequired = 1;
    // number of required taps (default is 1)
    private int mTapsRequired = 0;

    private boolean mAlwaysInTapRegion;
    private float mDownFocusX;
    private float mDownFocusY;
    private float mTouchSlopSquare;
    private float mAllowableMovementSquare;
    protected boolean mStarted;

    private int mNumTaps = 0;
    private int mNumTouches = 0;
    private final PointF mCurrentLocation;
    private boolean mBegan;

    /**
     * UILongPressGestureRecognizer looks for long-press gestures.
     * The user must press one or more fingers on a view and hold them there for a minimum period of time before the action
     * triggers.
     * While down, the user’s fingers may not move more than a specified distance; if they move beyond the specified distance,
     * the gesture fails.
     */
    public UILongPressGestureRecognizer(@Nullable final Context context) {
        super(context);

        mStarted = false;
        mBegan = false;
        mCurrentLocation = new PointF();

        int touchSlop;

        if (context == null) {
            //noinspection deprecation
            touchSlop = ViewConfiguration.getTouchSlop();
        } else {
            final ViewConfiguration configuration = ViewConfiguration.get(context);
            touchSlop = configuration.getScaledTouchSlop();
        }
        mTouchSlopSquare = touchSlop * touchSlop;
        mAllowableMovementSquare = mTouchSlopSquare;
    }

    @Override
    protected void handleMessage(final Message msg) {
        switch (msg.what) {
            case MESSAGE_RESET:
                //logMessage(Log.INFO, "handleMessage(MESSAGE_RESET)");
                handleReset();
                break;

            case MESSAGE_FAILED:
                //logMessage(Log.INFO, "handleMessage(MESSAGE_FAILED)");
                handleFailed();
                break;

            case MESSAGE_POINTER_UP:
                //logMessage(Log.INFO, "handleMessage(MESSAGE_POINTER_UP)");
                mNumTouches = msg.arg1;
                break;

            case MESSAGE_LONG_PRESS:
                //logMessage(Log.INFO, "handleMessage(MESSAGE_LONG_PRESS)");
                handleLongPress();
                break;

            default:
                break;
        }
    }

    @Override
    public int getNumberOfTouches() {
        return mNumTouches;
    }

    @Override
    public void onStateChanged(@NonNull final UIGestureRecognizer recognizer) {
        //logMessage(Log.VERBOSE, "onStateChanged(%s, %s)", recognizer, recognizer.getState());
        //logMessage(Log.VERBOSE, "started: %b, state: %s", mStarted, getState());

        if (recognizer.getState() == State.Failed && getState() == State.Began) {
            stopListenForOtherStateChanges();
            fireActionEventIfCanRecognizeSimultaneously();

            if (mBegan && hasBeganFiringEvents()) {
                setState(State.Changed);
            }

        } else if (recognizer.inState(State.Began, State.Ended) && mStarted && inState(State.Possible, State.Began)) {
            stopListenForOtherStateChanges();
            removeMessages();
            setState(State.Failed);
            setBeginFiringEvents(false);
            mStarted = false;
        }
    }

    /**
     * @param value The number of required taps for this recognizer to succeed.<br />
     *              Default value is 1
     * @since 1.0.0
     */
    public void setNumberOfTapsRequired(int value) {
        mTapsRequired = value;
    }

    /**
     * @param value The number of required touches for this recognizer to succeed.<br />
     *              Default value is 1
     * @since 1.0.0
     */
    public void setNumberOfTouchesRequired(int value) {
        mTouchesRequired = value;
    }

    @SuppressWarnings ("checkstyle:cyclomaticcomplexity")
    @Override
    public boolean onTouchEvent(final MotionEvent ev) {
        //Log.v(TAG, "onTouchEvent in long press recognizer");
        if (!isEnabled()) {
            return false;
        }

        final int action = ev.getAction();
        final int count = ev.getPointerCount();

        final boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;
        final int skipIndex = pointerUp ? ev.getActionIndex() : -1;

        // Determine focal point
        float sumX = 0, sumY = 0;
        for (int i = 0; i < count; i++) {
            if (skipIndex == i) {
                continue;
            }
            sumX += ev.getX(i);
            sumY += ev.getY(i);
        }

        final int div = pointerUp ? count - 1 : count;
        final float focusX = sumX / div;
        final float focusY = sumY / div;
        mCurrentLocation.set(focusX, focusY);

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                removeMessages();

                mAlwaysInTapRegion = true;
                mNumTouches = count;
                mBegan = false;

                if (!mStarted) {
                    stopListenForOtherStateChanges();
                    setState(State.Possible);
                    setBeginFiringEvents(false);
                    mNumTaps = 0;
                    mStarted = true;
                } else {
                    mNumTaps++;
                }

                if (mNumTaps == mTapsRequired) {
                    mHandler.sendEmptyMessageAtTime(MESSAGE_LONG_PRESS, ev.getDownTime() + longPressTimeOut);
                } else {
                    long timeout = LONG_PRESS_TIMEOUT;
                    if (timeout >= longPressTimeOut) {
                        timeout = longPressTimeOut - 1;
                    }
                    mHandler.sendEmptyMessageDelayed(MESSAGE_FAILED, timeout);
                }

                mDownFocusX = focusX;
                mDownFocusY = focusY;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (getState() == State.Possible && mStarted) {
                    removeMessages(MESSAGE_POINTER_UP);
                    mNumTouches = count;

                    if (mNumTouches > 1) {
                        if (mNumTouches > mTouchesRequired) {
                            removeMessages();
                            setState(State.Failed);
                        }
                    }

                    mDownFocusX = focusX;
                    mDownFocusY = focusY;
                } else if (inState(State.Began, State.Changed) && mStarted) {
                    mNumTouches = count;
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (getState() == State.Possible && mStarted) {
                    removeMessages(MESSAGE_POINTER_UP);

                    mDownFocusX = focusX;
                    mDownFocusY = focusY;

                    Message message = mHandler.obtainMessage(MESSAGE_POINTER_UP);
                    message.arg1 = mNumTouches - 1;
                    mHandler.sendMessageDelayed(message, TAP_TIMEOUT);
                } else if (inState(State.Began, State.Changed)) {
                    if (mNumTouches - 1 < mTouchesRequired) {
                        setState(State.Ended);

                        if (hasBeganFiringEvents()) {
                            fireActionEvent();
                        }

                        setBeginFiringEvents(false);
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (getState() == State.Possible && mStarted) {

                    if (mAlwaysInTapRegion) {
                        final float deltaX = (focusX - mDownFocusX);
                        final float deltaY = (focusY - mDownFocusY);
                        final float distance = (deltaX * deltaX) + (deltaY * deltaY);

                        if (distance > mAllowableMovementSquare) {
                            //logMessage(Log.WARN, "moved too much!: " + distance);
                            mAlwaysInTapRegion = false;
                            removeMessages();
                            setState(State.Failed);
                        }
                    }
                } else if (getState() == State.Began) {
                    if (!mBegan) {
                        final float deltaX = (focusX - mDownFocusX);
                        final float deltaY = (focusY - mDownFocusY);
                        final float distance = (deltaX * deltaX) + (deltaY * deltaY);

                        if (distance > mTouchSlopSquare) {
                            mBegan = true;

                            if (hasBeganFiringEvents()) {
                                setState(State.Changed);
                                fireActionEvent();
                            }
                        }
                    }
                } else if (getState() == State.Changed) {
                    setState(State.Changed);
                    if (hasBeganFiringEvents()) {
                        fireActionEvent();
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                removeMessages(MESSAGE_RESET, MESSAGE_POINTER_UP, MESSAGE_LONG_PRESS);

                if (getState() == State.Possible && mStarted) {
                    if (mNumTouches != mTouchesRequired) {
                        mStarted = false;
                        removeMessages();
                        setState(State.Failed);
                        postReset();
                    } else {
                        if (mNumTaps < mTapsRequired) {
                            removeMessages(MESSAGE_FAILED);
                            delayedFail();
                        } else {
                            mNumTaps = 0;
                            mStarted = false;
                            removeMessages();
                            setState(State.Failed);
                        }
                    }
                } else if (inState(State.Began, State.Changed)) {
                    mNumTaps = 0;
                    mStarted = false;
                    setState(State.Ended);
                    if (hasBeganFiringEvents()) {
                        fireActionEvent();
                    }
                    postReset();
                } else {
                    mStarted = false;
                    postReset();
                }
                setBeginFiringEvents(false);
                break;

            case MotionEvent.ACTION_CANCEL:
                removeMessages();
                mStarted = false;
                mNumTaps = 0;
                setState(State.Cancelled);
                postReset();
                break;

            default:
                break;

        }

        return getCancelsTouchesInView();
    }

    @Override
    protected void removeMessages() {
        removeMessages(MESSAGE_FAILED, MESSAGE_RESET, MESSAGE_POINTER_UP, MESSAGE_LONG_PRESS);
    }

    private void postReset() {
        mHandler.sendEmptyMessage(MESSAGE_RESET);
    }

    private void delayedFail() {
        mHandler.sendEmptyMessageDelayed(MESSAGE_FAILED, DOUBLE_TAP_TIMEOUT);
    }

    private void handleFailed() {
        removeMessages();
        setState(State.Failed);
        setBeginFiringEvents(false);

        mStarted = false;
    }

    private void handleReset() {
        setState(State.Possible);
        setBeginFiringEvents(false);
        mStarted = false;
    }

    public void handleLongPress() {
        Log.v("INFO", "handleLongPress");

        removeMessages(MESSAGE_FAILED);

        if (getState() == State.Possible && mStarted) {
            if (mNumTouches == mTouchesRequired && getDelegate().shouldBegin(this)) {
                setState(State.Began);
                if (null == getRequireFailureOf()) {
                    fireActionEventIfCanRecognizeSimultaneously();
                } else {
                    if (getRequireFailureOf().getState() == State.Failed) {
                        fireActionEventIfCanRecognizeSimultaneously();
                    } else if (getRequireFailureOf().inState(State.Began, State.Changed, State.Ended)) {
                        setState(State.Failed);
                        setBeginFiringEvents(false);
                        mStarted = false;
                        mNumTaps = 0;
                    } else {
                        listenForOtherStateChanges();
                        setBeginFiringEvents(false);
                        //logMessage(Log.DEBUG, "waiting...");
                    }
                }
            } else {
                setState(State.Failed);
                setBeginFiringEvents(false);
                mStarted = false;
                mNumTaps = 0;
            }
        }
    }

    private void fireActionEventIfCanRecognizeSimultaneously() {
        if (inState(State.Changed, State.Ended)) {
            setBeginFiringEvents(true);
            fireActionEvent();
        } else {
            if (getDelegate().shouldRecognizeSimultaneouslyWithGestureRecognizer(this)) {
                setBeginFiringEvents(true);
                fireActionEvent();
            }
        }
    }

    @Override
    protected boolean hasBeganFiringEvents() {
        return super.hasBeganFiringEvents() && inState(State.Began, State.Changed);
    }

    @Override
    public float getCurrentLocationX() {
        return mCurrentLocation.x;
    }

    @Override
    public float getCurrentLocationY() {
        return mCurrentLocation.y;
    }

    /**
     * @param value The maximum movement of the fingers on the view before the gesture fails.
     * @since 1.0.0
     */
    public void setAllowableMovement(final float value) {
        mAllowableMovementSquare = value * value;
    }

    /**
     * @return The maximum allowed movement of the fingers on the view before the gesture fails.
     * @since 1.0.0
     */
    public float getAllowableMovement() {
        return mAllowableMovementSquare;
    }

    /**
     * @param value The minimum period fingers must press on the view for the gesture to be recognized.<br />
     *              Value is in milliseconds
     * @since 1.0.0
     */
    public void setMinimumPressDuration(long value) {
        longPressTimeOut = value;
    }

    /**
     * @return The minimum period fingers must press on the view for the gesture to be recognized.
     * @since 1.0.0
     */
    public long getMinimumPressDuration() {
        return longPressTimeOut;
    }

}