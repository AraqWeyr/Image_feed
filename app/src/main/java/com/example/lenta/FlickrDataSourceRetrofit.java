package com.example.lenta;

import android.app.Application;
import android.content.Context;
import android.net.NetworkInfo;
import android.util.Log;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class FlickrDataSourceRetrofit implements IPhotoDataSource<FlickrPhoto> {
    private static final String BASE_URL = "https://api.flickr.com/services/rest/";
    private String apiKey = "a9decb6b1fe61cb692380ee04690c997";
    public static final String HEADER_CACHE_CONTROL = "Cache-Control";
    public static final String HEADER_PRAGMA = "Pragma";
    private String title;
    private final Application app;
    private final Retrofit mRetrofit;

    FlickrDataSourceRetrofit(Application application) {
        app = application;

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(provideOfflineCacheInterceptor())
                .addNetworkInterceptor(provideCacheInterceptor())
                .cache(provideCache()).build();
        mRetrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(new Gson()))
                .client(httpClient)
                .build();
    }

    public IFlickrApi getAPI() {
        return mRetrofit.create(IFlickrApi.class);
    }


    @Override
    public List<FlickrPhoto> getDataByPage(int pageNumber, int pageSize){
        List<FlickrPhoto> photos = new ArrayList<>();

        try
        {
            FlickrResponse response = getAPI().getPhotoPages(apiKey,pageSize, title, pageNumber + 1).execute().body();
            if(response != null)
            photos.addAll(response.getPhotos());
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        return photos;
    }

    @Override
    public void setSearchTile(String tile) {
        this.title = tile;
    }
    private Cache provideCache() {
        Cache cache = null;

        try {
            cache = new Cache(new File(app.getCacheDir(), "http-cache"),
                    4 * 1024 * 1024); // 4 MB
        } catch (Exception e) {
            Log.e("TAG", "Could not create Cache!");
        }

        return cache;
    }

    private Interceptor provideCacheInterceptor() {
        return chain -> {
            Response response = chain.proceed(chain.request());

            CacheControl cacheControl;

            if (isConnected()) {
                cacheControl = new CacheControl.Builder()
                        .maxAge(0, TimeUnit.SECONDS)
                        .build();
            } else {
                cacheControl = new CacheControl.Builder()
                        .maxStale(7, TimeUnit.DAYS)
                        .build();
            }

            return response.newBuilder()
                    .removeHeader(HEADER_PRAGMA)
                    .removeHeader(HEADER_CACHE_CONTROL)
                    .header(HEADER_CACHE_CONTROL, cacheControl.toString())
                    .build();

        };
    }

    private Interceptor provideOfflineCacheInterceptor() {
        return chain -> {
            Request request = chain.request();

            if (!isConnected()) {
                CacheControl cacheControl = new CacheControl.Builder()
                        .maxStale(7, TimeUnit.DAYS)
                        .build();

                request = request.newBuilder()
                        .removeHeader(HEADER_PRAGMA)
                        .removeHeader(HEADER_CACHE_CONTROL)
                        .cacheControl(cacheControl)
                        .build();
            }

            return chain.proceed(request);
        };
    }

    public boolean isConnected() {
        try {
            android.net.ConnectivityManager e = (android.net.ConnectivityManager) app.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = e.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } catch (Exception e) {
            Log.w("TAG", e.toString());
        }
        return false;
    }
}
