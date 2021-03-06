package com.rainmachine.domain.util.usecase

import io.reactivex.Observable

abstract class ObservableUseCase<in Q, P> {

    abstract fun execute(requestModel: Q): Observable<P>
}
