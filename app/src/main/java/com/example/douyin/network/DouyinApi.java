package com.example.douyin.network;

import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.BindPhoneRequest;
import com.example.douyin.network.model.CommentDto;
import com.example.douyin.network.model.CommentPage;
import com.example.douyin.network.model.FeedPage;
import com.example.douyin.network.model.LikeResult;
import com.example.douyin.network.model.LoginRequest;
import com.example.douyin.network.model.LoginResult;
import com.example.douyin.network.model.MatchPhonesRequest;
import com.example.douyin.network.model.MatchPhonesResult;
import com.example.douyin.network.model.PhoneLoginRequest;
import com.example.douyin.network.model.PhoneRegisterRequest;
import com.example.douyin.network.model.PostCommentRequest;
import com.example.douyin.network.model.RegisterRequest;
import com.example.douyin.network.model.SmsSendRequest;
import com.example.douyin.network.model.SmsSendResultDto;
import com.example.douyin.network.model.UserDto;
import com.example.douyin.network.model.UserProfileDto;
import com.example.douyin.network.model.VideoDto;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface DouyinApi {

    @POST("api/auth/register")
    Call<ApiResponse<LoginResult>> register(@Body RegisterRequest body);

    @POST("api/auth/login")
    Call<ApiResponse<LoginResult>> login(@Body LoginRequest body);

    @POST("api/auth/sms/send")
    Call<ApiResponse<SmsSendResultDto>> sendSms(@Body SmsSendRequest body);

    @POST("api/auth/phone/register")
    Call<ApiResponse<LoginResult>> registerByPhone(@Body PhoneRegisterRequest body);

    @POST("api/auth/phone/login")
    Call<ApiResponse<LoginResult>> loginByPhone(@Body PhoneLoginRequest body);

    @POST("api/users/me/phone")
    Call<ApiResponse<UserDto>> bindPhone(@Body BindPhoneRequest body);

    @GET("api/users/me")
    Call<ApiResponse<UserDto>> getMe();

    @POST("api/users/match-phones")
    Call<ApiResponse<MatchPhonesResult>> matchPhones(@Body MatchPhonesRequest body);

    @POST("api/users/{id}/follow")
    Call<ApiResponse<Boolean>> follow(@Path("id") long userId);

    @DELETE("api/users/{id}/follow")
    Call<ApiResponse<Boolean>> unfollow(@Path("id") long userId);

    @GET("api/users/me/following")
    Call<ApiResponse<List<UserDto>>> getMyFollowing();

    @GET("api/users/{id}")
    Call<ApiResponse<UserProfileDto>> getUserProfile(@Path("id") long userId);

    @GET("api/videos/feed")
    Call<ApiResponse<FeedPage>> getFeed(
            @Query("page") int page,
            @Query("size") int size
    );

    @Multipart
    @POST("api/videos")
    Call<ApiResponse<VideoDto>> publishVideo(
            @Part MultipartBody.Part video,
            @Part("description") RequestBody description,
            @Part MultipartBody.Part cover
    );

    @POST("api/videos/{id}/like")
    Call<ApiResponse<LikeResult>> toggleLike(@Path("id") long videoId);

    @GET("api/videos/{id}/comments")
    Call<ApiResponse<CommentPage>> getComments(
            @Path("id") long videoId,
            @Query("page") int page,
            @Query("size") int size
    );

    @POST("api/videos/{id}/comments")
    Call<ApiResponse<CommentDto>> postComment(
            @Path("id") long videoId,
            @Body PostCommentRequest body
    );

    @GET("api/users/{id}/videos")
    Call<ApiResponse<FeedPage>> getUserVideos(
            @Path("id") long userId,
            @Query("page") int page,
            @Query("size") int size
    );
}
