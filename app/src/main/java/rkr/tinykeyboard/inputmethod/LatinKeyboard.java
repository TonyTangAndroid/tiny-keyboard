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

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.Keyboard;
import android.view.inputmethod.EditorInfo;

public class LatinKeyboard extends Keyboard {

  public static final int KEYCODE_LANGUAGE_SWITCH = -101;

  private Key enterKey;
  private Key spaceKey;
  private Key languageSwitchKey;
  private Key savedSpaceKey;
  private Key savedLanguageSwitchKey;

  public LatinKeyboard(Context context, int xmlLayoutResId) {
    super(context, xmlLayoutResId);
  }

  @Override
  protected Key createKeyFromXml(
      Resources res, Row parent, int x, int y, XmlResourceParser parser) {
    Key key = new Key(res, parent, x, y, parser);
    if (key.codes[0] == Keyboard.KEYCODE_DONE) {
      enterKey = key;
    } else if (key.codes[0] == 32) {
      spaceKey = key;
      savedSpaceKey = new Key(res, parent, x, y, parser);
    } else if (key.codes[0] == KEYCODE_LANGUAGE_SWITCH) {
      languageSwitchKey = key;
      savedLanguageSwitchKey = new Key(res, parent, x, y, parser);
    }
    return key;
  }

  void setLanguageSwitchKeyVisibility(boolean visible) {
    if (visible) {
      spaceKey.width = savedSpaceKey.width;
      spaceKey.x = savedSpaceKey.x;
      languageSwitchKey.width = savedLanguageSwitchKey.width;
      languageSwitchKey.label = savedLanguageSwitchKey.label;
    } else {
      spaceKey.width = savedSpaceKey.width + savedLanguageSwitchKey.width;
      spaceKey.x = savedSpaceKey.x - savedLanguageSwitchKey.width;
      languageSwitchKey.width = 0;
      languageSwitchKey.label = null;
    }
  }

  void setImeOptions(Resources res, int options) {
    if (enterKey == null) {
      return;
    }

    switch (options & (EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
      case EditorInfo.IME_ACTION_GO:
        enterKey.label = res.getText(R.string.label_go_key);
        break;
      case EditorInfo.IME_ACTION_NEXT:
        enterKey.label = res.getText(R.string.label_next_key);
        break;
      case EditorInfo.IME_ACTION_SEARCH:
        enterKey.label = res.getString(R.string.search);
        break;
      case EditorInfo.IME_ACTION_SEND:
        enterKey.label = res.getText(R.string.label_send_key);
        break;
      default:
        enterKey.label = res.getString(R.string.enter);
        break;
    }
  }
}
