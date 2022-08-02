package com.criticalblue.auth.demo.books;

import android.util.Log;

import com.criticalblue.auth.demo.BooksApp;
import com.criticalblue.auth.demo.auth.AuthRepo;
import com.criticalblue.auth.demo.books.api.BookListResult;
import com.criticalblue.auth.demo.books.api.ImageLinks;
import com.criticalblue.auth.demo.books.api.Item;
import com.criticalblue.auth.demo.books.api.VolumeInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class BooksRepo {
    private static final String TAG = BooksRepo.class.getSimpleName();

    private static final String BOOKS_URL_BASE = "https://www.googleapis.com/books/v1/";

    private final BooksApp app;
    private final AuthRepo authRepo;
    private final BooksAPI searchBooksAPI;
    private final BooksAPI favouriteBooksAPI;

    public BooksRepo(BooksApp app, AuthRepo authRepo) {
        this.app = app;
        this.authRepo = authRepo;
        this.searchBooksAPI = createBooksAPI(false);
        this.favouriteBooksAPI = createBooksAPI(true);
    }

    private BooksAPI createBooksAPI(boolean withToken) {
        HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        logger.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        OkHttpClient client = app.getHttpClient();

        OkHttpClient.Builder clientBuilder = client.newBuilder();
        if (withToken) clientBuilder.addInterceptor(authRepo.getAccessTokenInterceptor());
        clientBuilder.addInterceptor(logger);

        Gson gson = new GsonBuilder().setLenient().create();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BOOKS_URL_BASE)
                .client(clientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        return retrofit.create(BooksAPI.class);
    }

    public void search(String query, BookListCallback callback) {
        if (query == null || query.trim().length() == 0 || callback == null) return;

        Log.e(TAG, BOOKS_URL_BASE + "?" + query);
        Call<BookListResult> request = searchBooksAPI.searchBooks(query);
        request.enqueue(new SearchCallback(query, callback));
    }

    private static class SearchCallback implements Callback<BookListResult> {
        private String query;
        private BookListCallback callback;
        public SearchCallback(String query, BookListCallback callback) {
            this.query = query;
            this.callback = callback;
        }

        @Override
        public void onResponse(Call<BookListResult> call, Response<BookListResult> response) {
            if(response.isSuccessful()) {
                BookListResult result = response.body();

                List<Book> books = new ArrayList<Book>();
                List<Item> items = result.getItems();
                if (items != null) {
                    for (Item item : items) {
                        VolumeInfo info = item.getVolumeInfo();
                        String title = info.getTitle();
                        StringBuilder authorsBuilder = new StringBuilder();
                        List<String> authorList = info.getAuthors();
                        String authors;
                        if (authorList != null && authorList.size() > 0) {
                            for (String author : info.getAuthors()) {
                                authorsBuilder.append(author);
                                authorsBuilder.append(", ");
                            }
                            authors = authorsBuilder.toString();
                        } else {
                            authors = "<none>";
                        }
                        ImageLinks imageLinks = info.getImageLinks();
                        String imageLink = (imageLinks != null) ? imageLinks.getThumbnail() : null;
                        if (imageLink != null) {
                            imageLink = imageLink.replace("edge=curl", "");
                        }

                        books.add(new Book(title, null, null, null,
                                authors, null, imageLink));
                    }
                }
                callback.call(query, books, null);
            } else {
                callback.call(query, Collections.emptyList(), new BooksException("Invalid searchBooks response"));
            }
        }

        @Override
        public void onFailure(Call<BookListResult> call, Throwable t) {
            callback.call(query, Collections.emptyList(),
                    new BooksException("Invalid searchBooks response: " + t.getMessage()));
        }
    }

    private static final String FAVORITES_ID = "0";

    public void findFavorites(BookListCallback callback) {
        Log.i(TAG, "Starting favorites");

        if (callback == null) return;

        fetchBookshelf(FAVORITES_ID, callback);
    }

    private void fetchBookshelf(String id, BookListCallback callback) {
        if (callback == null) return;

        Log.i(TAG, "Enqueing bookshelf call to fetch ID: " + id);

        Call<BookListResult> request = favouriteBooksAPI.getBookShelf(id);
        request.enqueue(new FavoritesCallback(id, callback));
    }

    private static class FavoritesCallback implements Callback<BookListResult> {
        private String uId;
        private String sId;
        private BookListCallback callback;
        public FavoritesCallback(String sId, BookListCallback callback) {
            this.sId = sId;
            this.callback = callback;
        }

        @Override
        public void onResponse(Call<BookListResult> call, Response<BookListResult> response) {
            if(response.isSuccessful()) {
                BookListResult result = response.body();

                Log.i(TAG, "Found good list of shelved books");

                List<Book> books = new ArrayList<Book>();
                List<Item> items = result.getItems();
                if (items != null) {
                    for (Item item : items) {
                        VolumeInfo info = item.getVolumeInfo();
                        String title = info.getTitle();
                        StringBuilder authorsBuilder = new StringBuilder();
                        List<String> authorList = info.getAuthors();
                        String authors;
                        if (authorList != null && authorList.size() > 0) {
                            for (String author : info.getAuthors()) {
                                authorsBuilder.append(author);
                                authorsBuilder.append(", ");
                            }
                            authors = authorsBuilder.toString();
                        } else {
                            authors = "<none>";
                        }
                        ImageLinks imageLinks = info.getImageLinks();
                        String imageLink = (imageLinks != null) ? imageLinks.getThumbnail() : null;
                        if (imageLink != null) {
                            imageLink = imageLink.replace("edge=curl", "");
                        }

                        books.add(new Book(title, null, null, null,
                                authors, null, imageLink));
                    }
                }
                callback.call(null, books, null);
            } else {
                callback.call(null, Collections.emptyList(), new BooksException("Invalid favorites response"));
            }
        }

        @Override
        public void onFailure(Call<BookListResult> call, Throwable t) {
            callback.call(null, Collections.emptyList(),
                    new BooksException("Invalid favorites response: " + t.getMessage()));
        }
    }
}
