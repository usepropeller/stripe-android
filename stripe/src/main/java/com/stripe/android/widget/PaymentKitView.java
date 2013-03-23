package com.stripe.android.widget;

import java.util.HashSet;

import android.content.Context;
import android.content.res.Resources;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.TranslateAnimation;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.stripe.android.R;
import com.stripe.android.model.Card;
import com.stripe.android.util.CardExpiry;
import com.stripe.android.util.CardNumberFormatter;
import com.stripe.android.util.TextUtils;

public class PaymentKitView extends FrameLayout {
    private static final long SLIDING_DURATION_MS = 500;

    private ImageView cardImageView;
    private EditText cardNumberView;
    private EditText expiryView;
    private EditText cvcView;

    private float cardNumberSlidingDelta = 0;
    private boolean isCardNumberCollapsed = false;
    private int cardNumberOriginalLeftMargin = -1;

    private Card card = new Card(null, null, null, null);
    private int textColor;
    private int errorColor;

    private InputFilter[] lengthOf3 = new InputFilter[] { new InputFilter.LengthFilter(3) };
    private InputFilter[] lengthOf4 = new InputFilter[] { new InputFilter.LengthFilter(4) };

    private boolean lastValidationResult = false;

    private HashSet<OnValidationChangeListener> listeners
        = new HashSet<OnValidationChangeListener>();

    public void registerListener(OnValidationChangeListener listener) {
        listeners.add(listener);
    }

    public void unregisterListener(OnValidationChangeListener listener) {
        listeners.remove(listener);
    }

    public interface OnValidationChangeListener {
        public void onChange(boolean valid);
    }

    public PaymentKitView(Context context) {
        super(context);
        init();
    }

    public PaymentKitView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PaymentKitView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public Card getCard() {
        return card;
    }

    private void init() {
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View parent = inflater.inflate(R.layout.__pk_view, this);

        cardImageView = (ImageView) parent.findViewById(R.id.__pk_card_image);
        cardNumberView = (EditText) parent.findViewById(R.id.__pk_card_number);
        expiryView = (EditText) parent.findViewById(R.id.__pk_expiry);
        cvcView = (EditText) parent.findViewById(R.id.__pk_cvc);

        Resources res = getContext().getResources();
        textColor = res.getColor(R.color.__pk_text_color);
        errorColor = res.getColor(R.color.__pk_error_color);

        CardNumberWatcher cardNumberWatcher = new CardNumberWatcher();
        cardNumberView.addTextChangedListener(cardNumberWatcher);
        cardNumberView.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && isCardNumberCollapsed) {
                    expandCardNumber();
                }
            }
        });

        ExpiryWatcher expiryWatcher = new ExpiryWatcher();
        expiryView.setFilters(new InputFilter[] { expiryWatcher });
        expiryView.addTextChangedListener(expiryWatcher);

        cvcView.addTextChangedListener(new CvcWatcher());
        cvcView.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    updateCvcType();
                } else {
                    updateCardType();
                }
            }
        });

        FrameLayout.LayoutParams params
            = (FrameLayout.LayoutParams) cardNumberView.getLayoutParams();
        cardNumberOriginalLeftMargin = params.leftMargin;
    }

    private float computeCardNumberSlidingDelta() {
        String number = cardNumberView.getText().toString();
        card.setNumber(number);
        int suffixLength = "American Express".equals(card.getType()) ? 5 : 4;

        Layout layout = cardNumberView.getLayout();
        return layout.getPrimaryHorizontal(number.length() - suffixLength);
    }

    private void collapseCardNumber() {
        cardNumberSlidingDelta = computeCardNumberSlidingDelta();
        isCardNumberCollapsed = true;
        animateCardNumber();
    }

    private void expandCardNumber() {
        isCardNumberCollapsed = false;
        animateCardNumber();
    }

    private void animateCardNumber() {
        float fromXDelta = isCardNumberCollapsed ? 0
                : -cardNumberSlidingDelta;
        float toXDelta = isCardNumberCollapsed ? -cardNumberSlidingDelta : 0;
        TranslateAnimation anim = new TranslateAnimation(fromXDelta, toXDelta,
                0, 0);
        anim.setDuration(SLIDING_DURATION_MS);
        anim.setAnimationListener(mAnimationListener);
        cardNumberView.startAnimation(anim);
    }

    private AnimationListener mAnimationListener = new AnimationListener() {
        public void onAnimationEnd(Animation animation) {
            if (!isCardNumberCollapsed) {
                return;
            }

            FrameLayout.LayoutParams params
                = (FrameLayout.LayoutParams) cardNumberView.getLayoutParams();
            params.leftMargin = (int) (cardNumberOriginalLeftMargin - cardNumberSlidingDelta);
            cardNumberView.setLayoutParams(params);

            expiryView.setVisibility(View.VISIBLE);
            cvcView.setVisibility(View.VISIBLE);

            expiryView.requestFocus();
        }

        public void onAnimationRepeat(Animation animation) {
            // not needed
        }

        public void onAnimationStart(Animation animation) {
            if (isCardNumberCollapsed) {
                return;
            }

            expiryView.setVisibility(View.GONE);
            cvcView.setVisibility(View.GONE);

            FrameLayout.LayoutParams params
                = (FrameLayout.LayoutParams) cardNumberView.getLayoutParams();
            params.leftMargin = cardNumberOriginalLeftMargin;
            cardNumberView.setLayoutParams(params);
        }
    };

    private void notifyValidationChange() {
        boolean valid = card.validateCard();
        if (valid != lastValidationResult) {
            for (OnValidationChangeListener listener : listeners) {
                listener.onChange(valid);
            }
        }
        lastValidationResult = valid;
    }


    public void updateCardType() {
        String cardType = card.getType();

        if ("American Express".equals(cardType)) {
            cardImageView.setImageResource(R.drawable.__pk_amex);
            return;
        }

        if ("Discover".equals(cardType)) {
            cardImageView.setImageResource(R.drawable.__pk_discover);
            return;
        }

        if ("JCB".equals(cardType)) {
            cardImageView.setImageResource(R.drawable.__pk_jcb);
            return;
        }

        if ("Diners Club".equals(cardType)) {
            cardImageView.setImageResource(R.drawable.__pk_diners);
            return;
        }

        if ("Visa".equals(cardType)) {
            cardImageView.setImageResource(R.drawable.__pk_visa);
            return;
        }

        if ("MasterCard".equals(cardType)) {
            cardImageView.setImageResource(R.drawable.__pk_mastercard);
            return;
        }

        cardImageView.setImageResource(R.drawable.__pk_placeholder);
    }

    public void updateCvcType() {
        boolean isAmex = "American Express".equals(card.getType());
        cardImageView.setImageResource(isAmex ? R.drawable.__pk_cvc_amex : R.drawable.__pk_cvc);
    }

    private class CardNumberWatcher implements TextWatcher {
        private boolean isInserting = false;

        @Override
        public void afterTextChanged(Editable s) {
            String number = s.toString();

            String formattedNumber = CardNumberFormatter.format(number, isInserting);
            if (!number.equals(formattedNumber)) {
                s.replace(0, s.length(), formattedNumber);
                return;
            }

            card.setNumber(number);
            cardNumberView.setTextColor(textColor);
            if (card.validateNumberLength()) {
                if (card.validateNumber()) {
                    collapseCardNumber();
                } else {
                    cardNumberView.setTextColor(errorColor);
                }
            }

            if ("American Express".equals(card.getType())) {
                cvcView.setFilters(lengthOf4);
            } else {
                cvcView.setFilters(lengthOf3);
            }

            updateCardType();

            notifyValidationChange();
        }
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            isInserting = (after > count);
        }
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // not needed
        }
    }

    private class ExpiryWatcher implements InputFilter, TextWatcher {
        private CardExpiry cardExpiry = new CardExpiry();
        private boolean isInserting = false;

        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                Spanned dest, int dstart, int dend) {
            StringBuffer buf = new StringBuffer();
            buf.append(dest.subSequence(0, dstart));
            buf.append(source.subSequence(start, end));
            buf.append(dest.subSequence(dend, dest.length()));
            String str = buf.toString();
            cardExpiry.updateFromString(str);
            return cardExpiry.isPartiallyValid() ? null : "";
        }

        @Override
        public void afterTextChanged(Editable s) {
            String str = s.toString();
            cardExpiry.updateFromString(str);
            card.setExpMonth(cardExpiry.getMonth());
            card.setExpYear(cardExpiry.getYear());
            if (cardExpiry.isPartiallyValid()) {
                String formattedString = isInserting ?
                        cardExpiry.toStringWithTrail() : cardExpiry.toString();
                if (!str.equals(formattedString)) {
                    s.replace(0, s.length(), formattedString);
                }
            }
            if (cardExpiry.isValid()) {
                cvcView.requestFocus();
            }

            notifyValidationChange();
        }
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            isInserting = (after > count);
        }
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // not needed
        }
    }

    private class CvcWatcher implements TextWatcher {
        @Override
        public void afterTextChanged(Editable s) {
            card.setCVC(TextUtils.nullIfBlank(s.toString()));
            notifyValidationChange();
        }
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // not needed
        }
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // not needed
        }
    }
}