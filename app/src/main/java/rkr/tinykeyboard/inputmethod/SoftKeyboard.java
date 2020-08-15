/*
 * Copyright (C) 2008-2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.tinykeyboard.inputmethod;

import android.app.Dialog;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.IBinder;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

public class SoftKeyboard extends InputMethodService
    implements KeyboardView.OnKeyboardActionListener {

  private InputMethodManager inputMethodManager;

  private KeyboardView inputView;

  private int lastDisplayWidth;
  private boolean capsLock;
  private long lastShiftTime;

  private LatinKeyboard symbolsKeyboard;
  private LatinKeyboard symbolsShiftedKeyboard;
  private LatinKeyboard qwertyKeyboard;

  private LatinKeyboard curKeyboard;

  @Override
  public void onCreate() {
    super.onCreate();
    inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
  }

  Context getDisplayContext() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      // createDisplayContext is not available.
      return this;
    }
    // TODO (b/133825283): Non-activity components Resources / DisplayMetrics update when
    //  moving to external display.
    // An issue in Q that non-activity components Resources / DisplayMetrics in
    // Context doesn't well updated when the IME window moving to external display.
    // Currently we do a workaround is to create new display context directly and re-init
    // keyboard layout with this context.
    final WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    return createDisplayContext(wm.getDefaultDisplay());
  }

  @Override
  public void onInitializeInterface() {
    final Context displayContext = getDisplayContext();

    if (qwertyKeyboard != null) {
      // Configuration changes can happen after the keyboard gets recreated,
      // so we need to be able to re-build the keyboards if the available
      // space has changed.
      int displayWidth = getMaxWidth();
      if (displayWidth == lastDisplayWidth) {
        return;
      }
      lastDisplayWidth = displayWidth;
    }
    qwertyKeyboard = new LatinKeyboard(displayContext, R.xml.qwerty);
    symbolsKeyboard = new LatinKeyboard(displayContext, R.xml.symbols);
    symbolsShiftedKeyboard = new LatinKeyboard(displayContext, R.xml.symbols_shift);
  }

  @Override
  public View onCreateInputView() {
    inputView = (KeyboardView) getLayoutInflater().inflate(R.layout.input, null);
    inputView.setOnKeyboardActionListener(this);
    inputView.setPreviewEnabled(false);
    setLatinKeyboard(qwertyKeyboard);
    return inputView;
  }

  private void setLatinKeyboard(LatinKeyboard nextKeyboard) {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
      final boolean shouldSupportLanguageSwitchKey =
          inputMethodManager.shouldOfferSwitchingToNextInputMethod(getToken());
      nextKeyboard.setLanguageSwitchKeyVisibility(shouldSupportLanguageSwitchKey);
    }
    inputView.setKeyboard(nextKeyboard);
  }

  @Override
  public void onStartInput(EditorInfo attribute, boolean restarting) {
    super.onStartInput(attribute, restarting);

    // We are now going to initialize our state based on the type of
    // text being edited.
    switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
      case InputType.TYPE_CLASS_NUMBER:
      case InputType.TYPE_CLASS_DATETIME:
      case InputType.TYPE_CLASS_PHONE:
        // Numbers and dates default to the symbols keyboard, with
        // no extra features.
        curKeyboard = symbolsKeyboard;
        break;

      default:
        // For all unknown input types, default to the alphabetic
        // keyboard with no special features.
        curKeyboard = qwertyKeyboard;
        updateShiftKeyState(attribute);
    }

    // Update the label on the enter key, depending on what the application
    // says it will do.
    curKeyboard.setImeOptions(getResources(), attribute.imeOptions);
  }

  @Override
  public void onFinishInput() {
    super.onFinishInput();

    curKeyboard = qwertyKeyboard;
    if (inputView != null) {
      inputView.closing();
    }
  }

  @Override
  public void onStartInputView(EditorInfo attribute, boolean restarting) {
    super.onStartInputView(attribute, restarting);
    // Apply the selected keyboard to the input view.
    setLatinKeyboard(curKeyboard);
    inputView.closing();
  }

  private void updateShiftKeyState(EditorInfo attr) {
    if (attr != null && inputView != null && qwertyKeyboard == inputView.getKeyboard()) {
      int caps = 0;
      EditorInfo ei = getCurrentInputEditorInfo();
      if (ei != null && ei.inputType != InputType.TYPE_NULL) {
        caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
      }
      inputView.setShifted(capsLock || caps != 0);
    }
  }

  private void keyDownUp(int keyEventCode) {
    getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
    getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
  }

  // Implementation of KeyboardViewListener

  public void onKey(int primaryCode, int[] keyCodes) {
    if (primaryCode == Keyboard.KEYCODE_DONE) {
      keyDownUp(KeyEvent.KEYCODE_ENTER);
    } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
      handleBackspace();
    } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
      handleShift();
    } else if (primaryCode == LatinKeyboard.KEYCODE_LANGUAGE_SWITCH) {
      handleLanguageSwitch();
    } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE && inputView != null) {
      Keyboard current = inputView.getKeyboard();
      if (current == symbolsKeyboard || current == symbolsShiftedKeyboard) {
        setLatinKeyboard(qwertyKeyboard);
      } else {
        setLatinKeyboard(symbolsKeyboard);
        symbolsKeyboard.setShifted(false);
      }
    } else {
      handleCharacter(primaryCode);
    }
  }

  public void onText(CharSequence text) {}

  private void handleBackspace() {
    keyDownUp(KeyEvent.KEYCODE_DEL);
    updateShiftKeyState(getCurrentInputEditorInfo());
  }

  private void handleShift() {
    if (inputView == null) {
      return;
    }

    Keyboard currentKeyboard = inputView.getKeyboard();
    if (qwertyKeyboard == currentKeyboard) {
      // Alphabet keyboard
      checkToggleCapsLock();
      inputView.setShifted(capsLock || !inputView.isShifted());
    } else if (currentKeyboard == symbolsKeyboard) {
      symbolsKeyboard.setShifted(true);
      setLatinKeyboard(symbolsShiftedKeyboard);
      symbolsShiftedKeyboard.setShifted(true);
    } else if (currentKeyboard == symbolsShiftedKeyboard) {
      symbolsShiftedKeyboard.setShifted(false);
      setLatinKeyboard(symbolsKeyboard);
      symbolsKeyboard.setShifted(false);
    }
  }

  private void handleCharacter(int primaryCode) {
    if (isInputViewShown()) {
      if (inputView.isShifted()) {
        primaryCode = Character.toUpperCase(primaryCode);
      }
    }
    getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
    updateShiftKeyState(getCurrentInputEditorInfo());
  }

  private IBinder getToken() {
    final Dialog dialog = getWindow();
    if (dialog == null) {
      return null;
    }
    final Window window = dialog.getWindow();
    if (window == null) {
      return null;
    }
    return window.getAttributes().token;
  }

  private void handleLanguageSwitch() {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
      inputMethodManager.switchToNextInputMethod(getToken(), false /* onlyCurrentIme */);
    }
  }

  private void checkToggleCapsLock() {
    long now = System.currentTimeMillis();
    if (lastShiftTime + 800 > now) {
      capsLock = !capsLock;
      lastShiftTime = 0;
    } else {
      lastShiftTime = now;
    }
  }

  public void swipeRight() {}

  public void swipeLeft() {}

  public void swipeDown() {}

  public void swipeUp() {}

  public void onPress(int primaryCode) {}

  public void onRelease(int primaryCode) {}
}
