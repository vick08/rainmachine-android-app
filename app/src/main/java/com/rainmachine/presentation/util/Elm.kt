package com.rainmachine.presentation.util

import com.jakewharton.rxrelay2.BehaviorRelay
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.*

sealed class AbstractState
open class ElmState : AbstractState()

sealed class AbstractMsg
open class Msg : AbstractMsg()
object Idle : Msg()
class ErrorMsg(val err: Throwable, val cmd: Cmd) : Msg()


sealed class AbstractCmd
open class Cmd : AbstractCmd()
object None : Cmd()

interface ElmPresenter {

    fun update(msg: Msg, state: ElmState): Pair<ElmState, Cmd>

    fun render(state: ElmState)

    fun call(cmd: Cmd): Single<Msg>
}


class Elm {

    private val uiScheduler: Scheduler = AndroidSchedulers.mainThread()
    private val msgRelay: BehaviorRelay<Pair<Msg, ElmState>> = BehaviorRelay.create()
    private var msgQueue = ArrayDeque<Msg>()
    private lateinit var state: ElmState
    private lateinit var elmPresenter: ElmPresenter

    fun init(initialState: ElmState, elmPresenter: ElmPresenter): Disposable {
        this.elmPresenter = elmPresenter
        this.state = initialState
        return msgRelay
                .map { (msg, state) ->
                    Timber.d("elm update msg:$msg ")
                    elmPresenter.update(msg, state)
                }
                .observeOn(uiScheduler)
                .doOnNext { (state, _) ->
                    if (state != getState()) {
                        elmPresenter.render(state)
                    }
                }
                .doOnNext { (state, _) ->
                    this.state = state
                    if (msgQueue.size > 0) {
                        msgQueue.removeFirst()
                    }
                    loop()
                }
                .filter { (_, cmd) -> cmd !is None }
                .observeOn(Schedulers.io())
                .flatMap { (state, cmd) ->
                    Timber.d("elm call cmd:$cmd state:$state ")
                    return@flatMap elmPresenter.call(cmd)
                            .onErrorResumeNext { err -> Single.just(ErrorMsg(err, cmd)) }
                            .toObservable()

                }
                .observeOn(uiScheduler)
                .subscribe({ msg ->
                    Timber.d("elm subscribe msg:${msg.javaClass.simpleName}")
                    when (msg) {
                        is Idle -> {
                        }
                        else -> msgQueue.addLast(msg)
                    }

                    loop()
                })
    }

    fun getState(): ElmState = state

    fun render() {
        elmPresenter.render(state)
    }

    private fun loop() {
        if (msgQueue.size > 0) {
            msgRelay.accept(Pair(msgQueue.first, this.state))
        }
    }

    fun accept(msg: Msg) {
        Timber.d("elm accept event:${msg.javaClass.simpleName}")
        msgQueue.addLast(msg)
        if (msgQueue.size == 1) {
            msgRelay.accept(Pair(msgQueue.first, state))
        }
    }

}