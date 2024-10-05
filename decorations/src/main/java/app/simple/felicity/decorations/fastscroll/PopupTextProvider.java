package app.simple.felicity.decorations.fastscroll;

import androidx.annotation.NonNull;

public interface PopupTextProvider {
    @NonNull
    String getPopupText(int position);
}
