package com.rainmachine.data.remote.util;

import com.rainmachine.data.remote.sprinkler.v3.SprinklerApiUtils3;
import com.rainmachine.data.util.DataException;
import com.rainmachine.infrastructure.SprinklerUtils;

import java.io.IOException;

import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.SingleTransformer;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import retrofit2.HttpException;

public class SprinklerRemoteErrorTransformer3 implements SingleTransformer<Object, Object> {

    private SprinklerUtils sprinklerUtils;
    private SprinklerApiUtils3 sprinklerApiUtils3;

    public SprinklerRemoteErrorTransformer3(SprinklerUtils sprinklerUtils,
                                            SprinklerApiUtils3 sprinklerApiUtils3) {
        this.sprinklerUtils = sprinklerUtils;
        this.sprinklerApiUtils3 = sprinklerApiUtils3;
    }

    @Override
    public SingleSource<Object> apply(@NonNull Single<Object> upstream) {
        return upstream
                .onErrorReturn(wrapError)
                .doOnError(onError);
    }

    private final Function<Throwable, Object> wrapError = throwable -> {
        if (throwable instanceof IOException) {
            throw new DataException(DataException.Status.NETWORK_ERROR, throwable);
        } else if (throwable instanceof HttpException) {
            HttpException httpException = (HttpException) throwable;
            if (sprinklerApiUtils3.isAuthenticationFailure(httpException)) {
                throw new DataException(DataException.Status.AUTHENTICATION_ERROR, throwable);
            }
            throw new DataException(DataException.Status.HTTP_GENERIC_ERROR, throwable);
        } else if (throwable instanceof ApiMapperException) {
            throw new DataException(DataException.Status.API_MAPPER_ERROR, throwable);
        }
        throw new DataException(DataException.Status.UNKNOWN, throwable);
    };

    private final Consumer<Throwable> onError = throwable -> {
        if (throwable instanceof DataException) {
            DataException dataException = (DataException) throwable;
            if (dataException.status == DataException.Status.AUTHENTICATION_ERROR) {
                sprinklerUtils.dealWithSessionExpiration();
            }
        }
    };
}
