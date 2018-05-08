
package com.payubizwrapper;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.payu.india.Extras.PayUChecksum;
import com.payu.india.Model.PaymentParams;
import com.payu.india.Model.PayuConfig;
import com.payu.india.Model.PayuHashes;
import com.payu.india.Model.PostData;
import com.payu.india.Payu.Payu;
import com.payu.india.Payu.PayuConstants;
import com.payu.india.Payu.PayuErrors;
import com.payu.payuui.Activity.PayUBaseActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class RNPayubizWrapperModule extends ReactContextBaseJavaModule {
  private static final String STAGING_ENV = "STAGING_ENV";
  private static final String PRODUCTION_ENV = "PRODUCTION_ENV";
  private int environemnt;
  private String merchentKey;
  private String userCredentials;
  private String salt;
  private PaymentParams mPaymentParams;
  private Promise promise;
  private PayuConfig payuConfig;
  private PayUChecksum checksum;
  private final ReactApplicationContext reactContext;

  public RNPayubizWrapperModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNPayubizWrapper";
  }

  @Nullable
  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put(STAGING_ENV, PayuConstants.STAGING_ENV);
    constants.put(PRODUCTION_ENV, PayuConstants.PRODUCTION_ENV);
    return constants;
  }
  @ReactMethod
  public void init(int environemnt,String merchentKey,String salt) {
    this.environemnt=environemnt;
    this.merchentKey=merchentKey;
    this.salt=salt;

  }

  @ReactMethod
  public void pay(ReadableMap params, Promise promise) {
    this.promise = promise;
    Payu.setInstance(getCurrentActivity());
    mPaymentParams = new PaymentParams();
    String amount = params.getString("amount");
    String product_info = params.getString("product_info");
    String firstname = params.getString("firstname");
    String email = params.getString("email");
    String phone = params.getString("phone");
    String surl = params.getString("surl");
    String furl = params.getString("furl");
    String udf1 = params.getString("udf1");
    String udf2 = params.getString("udf2");
    String udf3 = params.getString("udf3");
    String udf4 = params.getString("udf4");
    String udf5 = params.getString("udf5");
    userCredentials=merchentKey + ":" + email;
    Long transationId=System.currentTimeMillis();
    mPaymentParams.setKey(merchentKey);
    mPaymentParams.setAmount(amount);
    mPaymentParams.setProductInfo(product_info);
    mPaymentParams.setFirstName(firstname);
    mPaymentParams.setEmail(email);
    mPaymentParams.setPhone(phone);
    mPaymentParams.setTxnId("" + transationId);
    mPaymentParams.setSurl(surl);
    mPaymentParams.setFurl(furl);
    mPaymentParams.setNotifyURL(mPaymentParams.getSurl());
    mPaymentParams.setUdf1(udf1);
    mPaymentParams.setUdf2(udf2);
    mPaymentParams.setUdf3(udf3);
    mPaymentParams.setUdf4(udf4);
    mPaymentParams.setUdf5(udf5);
    mPaymentParams.setUserCredentials(userCredentials);
    payuConfig = new PayuConfig();
    payuConfig.setEnvironment(environemnt);
    generateHashFromSDK(mPaymentParams,salt);
  }

  public void generateHashFromSDK(PaymentParams mPaymentParams, String salt) {
    PayuHashes payuHashes = new PayuHashes();
    PostData postData = new PostData();

    // payment Hash;
    checksum = null;
    checksum = new PayUChecksum();
    checksum.setAmount(mPaymentParams.getAmount());
    checksum.setKey(mPaymentParams.getKey());
    checksum.setTxnid(mPaymentParams.getTxnId());
    checksum.setEmail(mPaymentParams.getEmail());
    checksum.setSalt(salt);
    checksum.setProductinfo(mPaymentParams.getProductInfo());
    checksum.setFirstname(mPaymentParams.getFirstName());
    checksum.setUdf1(mPaymentParams.getUdf1());
    checksum.setUdf2(mPaymentParams.getUdf2());
    checksum.setUdf3(mPaymentParams.getUdf3());
    checksum.setUdf4(mPaymentParams.getUdf4());
    checksum.setUdf5(mPaymentParams.getUdf5());

    postData = checksum.getHash();
    if (postData.getCode() == PayuErrors.NO_ERROR) {
      payuHashes.setPaymentHash(postData.getResult());
    }

    // checksum for payemnt related details
    // var1 should be either user credentials or default
    String var1 = mPaymentParams.getUserCredentials() == null ? PayuConstants.DEFAULT : mPaymentParams.getUserCredentials();
    String key = mPaymentParams.getKey();

    if ((postData = calculateHash(key, PayuConstants.PAYMENT_RELATED_DETAILS_FOR_MOBILE_SDK, var1, salt)) != null && postData.getCode() == PayuErrors.NO_ERROR) // Assign post data first then check for success
      payuHashes.setPaymentRelatedDetailsForMobileSdkHash(postData.getResult());
    //vas
    if ((postData = calculateHash(key, PayuConstants.VAS_FOR_MOBILE_SDK, PayuConstants.DEFAULT, salt)) != null && postData.getCode() == PayuErrors.NO_ERROR)
      payuHashes.setVasForMobileSdkHash(postData.getResult());

    // getIbibocodes
    if ((postData = calculateHash(key, PayuConstants.GET_MERCHANT_IBIBO_CODES, PayuConstants.DEFAULT, salt)) != null && postData.getCode() == PayuErrors.NO_ERROR)
      payuHashes.setMerchantIbiboCodesHash(postData.getResult());

    if (!var1.contentEquals(PayuConstants.DEFAULT)) {
      // get user card
      if ((postData = calculateHash(key, PayuConstants.GET_USER_CARDS, var1, salt)) != null && postData.getCode() == PayuErrors.NO_ERROR) // todo rename storedc ard
        payuHashes.setStoredCardsHash(postData.getResult());
      // save user card
      if ((postData = calculateHash(key, PayuConstants.SAVE_USER_CARD, var1, salt)) != null && postData.getCode() == PayuErrors.NO_ERROR)
        payuHashes.setSaveCardHash(postData.getResult());
      // delete user card
      if ((postData = calculateHash(key, PayuConstants.DELETE_USER_CARD, var1, salt)) != null && postData.getCode() == PayuErrors.NO_ERROR)
        payuHashes.setDeleteCardHash(postData.getResult());
      // edit user card
      if ((postData = calculateHash(key, PayuConstants.EDIT_USER_CARD, var1, salt)) != null && postData.getCode() == PayuErrors.NO_ERROR)
        payuHashes.setEditCardHash(postData.getResult());
    }

    if (mPaymentParams.getOfferKey() != null) {
      postData = calculateHash(key, PayuConstants.OFFER_KEY, mPaymentParams.getOfferKey(), salt);
      if (postData.getCode() == PayuErrors.NO_ERROR) {
        payuHashes.setCheckOfferStatusHash(postData.getResult());
      }
    }

    if (mPaymentParams.getOfferKey() != null && (postData = calculateHash(key, PayuConstants.CHECK_OFFER_STATUS, mPaymentParams.getOfferKey(), salt)) != null && postData.getCode() == PayuErrors.NO_ERROR) {
      payuHashes.setCheckOfferStatusHash(postData.getResult());
    }

    // we have generated all the hases now lest launch sdk's ui
    launchSdkUI(payuHashes);
  }

  private PostData calculateHash(String key, String command, String var1, String salt) {
    checksum = null;
    checksum = new PayUChecksum();
    checksum.setKey(key);
    checksum.setCommand(command);
    checksum.setVar1(var1);
    checksum.setSalt(salt);
    return checksum.getHash();
  }
  public void launchSdkUI(PayuHashes payuHashes) {
    Log.d("environment",environemnt+"");
    Log.d("name",mPaymentParams.getFirstName());
    Log.d("amount",mPaymentParams.getAmount());
    Log.d("product_info",mPaymentParams.getProductInfo());
    Activity mActivity=getCurrentActivity();
    Intent intent = new Intent(mActivity, PayUBaseActivity.class);
    intent.putExtra(PayuConstants.PAYU_CONFIG, payuConfig);
    intent.putExtra(PayuConstants.PAYMENT_PARAMS, mPaymentParams);
    intent.putExtra(PayuConstants.PAYU_HASHES, payuHashes);

    //Lets fetch all the one click card tokens first
    fetchMerchantHashes(intent);

  }

  private void fetchMerchantHashes(final Intent intent) {
    // now make the api call.
    final String postParams = "merchant_key=" + merchentKey + "&user_credentials=" + userCredentials;
    final Intent baseActivityIntent = intent;
    new AsyncTask<Void, Void, HashMap<String, String>>() {

      @Override
      protected HashMap<String, String> doInBackground(Void... params) {
        try {
          //TODO Replace below url with your server side file url.
          URL url = new URL("https://payu.herokuapp.com/get_merchant_hashes");

          byte[] postParamsByte = postParams.getBytes("UTF-8");

          HttpURLConnection conn = (HttpURLConnection) url.openConnection();
          conn.setRequestMethod("GET");
          conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
          conn.setRequestProperty("Content-Length", String.valueOf(postParamsByte.length));
          conn.setDoOutput(true);
          conn.getOutputStream().write(postParamsByte);

          InputStream responseInputStream = conn.getInputStream();
          StringBuffer responseStringBuffer = new StringBuffer();
          byte[] byteContainer = new byte[1024];
          for (int i; (i = responseInputStream.read(byteContainer)) != -1; ) {
            responseStringBuffer.append(new String(byteContainer, 0, i));
          }

          JSONObject response = new JSONObject(responseStringBuffer.toString());

          HashMap<String, String> cardTokens = new HashMap<String, String>();
          JSONArray oneClickCardsArray = response.getJSONArray("data");
          int arrayLength;
          if ((arrayLength = oneClickCardsArray.length()) >= 1) {
            for (int i = 0; i < arrayLength; i++) {
              cardTokens.put(oneClickCardsArray.getJSONArray(i).getString(0), oneClickCardsArray.getJSONArray(i).getString(1));
            }
            return cardTokens;
          }
          // pass these to next activity

        } catch (JSONException e) {
          e.printStackTrace();
        } catch (MalformedURLException e) {
          e.printStackTrace();
        } catch (ProtocolException e) {
          e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
        return null;
      }

      @Override
      protected void onPostExecute(HashMap<String, String> oneClickTokens) {
        super.onPostExecute(oneClickTokens);
        Activity mActivity=getCurrentActivity();
        baseActivityIntent.putExtra(PayuConstants.ONE_CLICK_CARD_TOKENS, oneClickTokens);
        mActivity.startActivityForResult(baseActivityIntent, PayuConstants.PAYU_REQUEST_CODE);
      }
    }.execute();
  }
}