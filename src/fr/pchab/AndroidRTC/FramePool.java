/*
 * libjingle
 * Copyright 2013, Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package fr.pchab.AndroidRTC;

import org.webrtc.VideoRenderer.I420Frame;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * This class acts as an allocation pool meant to minimize GC churn caused by
 * frame allocation & disposal.  The public API comprises of just two methods:
 * copyFrame(), which allocates as necessary and copies, and
 * returnFrame(), which returns frame ownership to the pool for use by a later
 * call to copyFrame().
 *
 * This class is thread-safe; calls to copyFrame() and returnFrame() are allowed
 * to happen on any thread.
 */
class FramePool {
    // Maps each summary code (see summarizeFrameDimensions()) to a list of frames
    // of that description.
    private final HashMap<Long, LinkedList<I420Frame>> availableFrames =
            new HashMap<Long, LinkedList<I420Frame>>();
    // Every dimension (e.g. width, height, stride) of a frame must be less than
    // this value.
    private static final long MAX_DIMENSION = 4096;

    public I420Frame takeFrame(I420Frame source) {
        long desc = summarizeFrameDimensions(source);
        I420Frame dst = null;
        synchronized (availableFrames) {
            LinkedList<I420Frame> frames = availableFrames.get(desc);
            if (frames == null) {
                frames = new LinkedList<I420Frame>();
                availableFrames.put(desc, frames);
            }
            if (!frames.isEmpty()) {
                dst = frames.pop();
            } else {
                dst = new I420Frame(
                        source.width, source.height, source.yuvStrides, null);
            }
        }
        return dst;
    }

    public void returnFrame(I420Frame frame) {
        long desc = summarizeFrameDimensions(frame);
        synchronized (availableFrames) {
            LinkedList<I420Frame> frames = availableFrames.get(desc);
            if (frames == null) {
                throw new IllegalArgumentException("Unexpected frame dimensions");
            }
            frames.add(frame);
        }
    }

    /** Validate that |frame| can be managed by the pool. */
    public static boolean validateDimensions(I420Frame frame) {
        return frame.width < MAX_DIMENSION && frame.height < MAX_DIMENSION &&
                frame.yuvStrides[0] < MAX_DIMENSION &&
                frame.yuvStrides[1] < MAX_DIMENSION &&
                frame.yuvStrides[2] < MAX_DIMENSION;
    }

    // Return a code summarizing the dimensions of |frame|.  Two frames that
    // return the same summary are guaranteed to be able to store each others'
    // contents.  Used like Object.hashCode(), but we need all the bits of a long
    // to do a good job, and hashCode() returns int, so we do this.
    private static long summarizeFrameDimensions(I420Frame frame) {
        long ret = frame.width;
        ret = ret * MAX_DIMENSION + frame.height;
        ret = ret * MAX_DIMENSION + frame.yuvStrides[0];
        ret = ret * MAX_DIMENSION + frame.yuvStrides[1];
        ret = ret * MAX_DIMENSION + frame.yuvStrides[2];
        return ret;
    }
}
