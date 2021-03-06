package top.rechinx.meow.ui.reader

import com.jakewharton.rxrelay2.BehaviorRelay
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import top.rechinx.meow.core.source.SourceManager
import top.rechinx.meow.data.database.model.Manga
import top.rechinx.meow.data.repository.ChapterRepository
import top.rechinx.meow.data.repository.MangaRepository
import top.rechinx.meow.support.log.L
import top.rechinx.meow.ui.reader.loader.ChapterLoader
import top.rechinx.meow.ui.reader.model.ReaderChapter
import top.rechinx.meow.ui.reader.model.ReaderPage
import top.rechinx.meow.ui.reader.model.ViewerChapters
import top.rechinx.rikka.mvp.BasePresenter
import top.rechinx.rikka.rxbus.RxBus
import java.util.*
import java.util.concurrent.TimeUnit

class ReaderPresenter : BasePresenter<ReaderActivity>(), KoinComponent{

    private val sourceManager: SourceManager by inject()
    private val mangaRepository: MangaRepository by inject()
    private val chapterRepository: ChapterRepository by inject()

    var manga: Manga? = null
        private set

    private val chapterList by lazy {
        val manga = manga!!
        val chapters = chapterRepository.getLocalChapters(manga).reversed()
        chapters.map(::ReaderChapter)
    }

    private val viewerChaptersRelay = BehaviorRelay.create<ViewerChapters>()
    private var chapterId = -1L

    private var activeChapterDisposable: Disposable ?= null

    private var loader: ChapterLoader? = null

    fun needsInit() : Boolean = manga == null

    fun loadInit(mangaId: Long, initialChapterId: Long, isContinued: Boolean) {
        if(!needsInit()) return
            mangaRepository.getManga(mangaId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext { loadInit(it, initialChapterId, isContinued) }
                    .subscribeFirst({_, _ -> }, {_, e -> L.d(e.message)})
    }

    fun loadInit(manga: Manga, initialChapterId: Long, isContinued: Boolean) {
        this.manga = manga
        if(chapterId == -1L) chapterId = initialChapterId

        val source = sourceManager.getOrStub(manga.sourceId)
        loader = ChapterLoader(manga, source)

        Observable.just(manga).subscribeLatestCache(ReaderActivity::setManga)
        viewerChaptersRelay.subscribeLatestCache(ReaderActivity::setChapters)

        activeChapterDisposable?.dispose()
        activeChapterDisposable = Observable
                .fromCallable { chapterList.first{ chapterId == it.chapter.id} }
                .flatMap { getLoadObservable(loader!!, it, isContinued) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeFirst({_, _ -> }, {_, e -> L.d(e.message)})
    }

    private fun getLoadObservable(loader: ChapterLoader, chapter: ReaderChapter, isContinued: Boolean = false): Observable<ViewerChapters> {
        return loader.loadChapter(chapter, isContinued)
                .andThen(Observable.fromCallable {
                    val chapterPos = chapterList.indexOf(chapter)
                    ViewerChapters(chapter,
                            chapterList.getOrNull(chapterPos - 1),
                            chapterList.getOrNull(chapterPos + 1))
                })
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { newChapters ->
                    val oldChapters = viewerChaptersRelay.value
                    newChapters.ref()
                    oldChapters?.unref()
                    viewerChaptersRelay.accept(newChapters)
                }
    }

    fun preloadChapter(chapter: ReaderChapter) {
        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }

        val loader = loader ?: return

        loader.loadChapter(chapter)
                .observeOn(AndroidSchedulers.mainThread())
                // Update current chapters whenever a chapter is preloaded
                .doOnComplete { viewerChaptersRelay.value?.let(viewerChaptersRelay::accept) }
                .onErrorComplete()
                .subscribe()
                .also(::add)
    }

    fun onPageSelected(page: ReaderPage) {
        val currentChapters = viewerChaptersRelay.value ?: return

        val selectedChapter = page.chapter
        selectedChapter.chapter.last_page_read = page.index

        if (selectedChapter != currentChapters.currChapter) {
            onChapterChanged(currentChapters.currChapter, selectedChapter)
            loadNewChapter(selectedChapter)
        }
    }

    private fun loadNewChapter(chapter: ReaderChapter) {
        val loader = loader ?: return

        activeChapterDisposable?.dispose()
        activeChapterDisposable = getLoadObservable(loader, chapter)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
                .also(::add)
    }

    private fun onChapterChanged(fromChapter: ReaderChapter, toChapter: ReaderChapter) {
        saveChapterProgress(fromChapter)
        saveChapterHistory(fromChapter)
        saveLastRead(toChapter)
    }

    private fun saveChapterProgress(chapter: ReaderChapter) {
        chapterRepository.updateChapter(chapter.chapter)
    }

    private fun saveChapterHistory(chapter: ReaderChapter) {
    }

    fun getCurrentChapter(): ReaderChapter? {
        return viewerChaptersRelay.value?.currChapter
    }

    fun setMangaViewer(viewer: Int) {
        val manga = manga ?: return
        manga.viewer = viewer
        mangaRepository.updateManga(manga)
        Observable.timer(250, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .subscribeFirst({ view, _ ->
                    val currChapters = viewerChaptersRelay.value
                    if (currChapters != null) {
                        // Save current page
                        val currChapter = currChapters.currChapter
                        currChapter.requestedPage = currChapter.chapter.last_page_read

                        // Emit manga and chapters to the new viewer
                        view.setManga(manga)
                        view.setChapters(currChapters)
                    }
                })
    }

    fun saveLastRead(chapter: ReaderChapter?) {
        if(chapter != null) {
            manga?.last_read_chapter_id = chapter.chapter.id
            RxBus.instance?.post(manga!!)
            add(mangaRepository.updateManga(manga!!))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val currentChapters = viewerChaptersRelay.value
        if (currentChapters != null) {
            currentChapters.unref()
            saveChapterProgress(currentChapters.currChapter)
            saveChapterHistory(currentChapters.currChapter)
        }
    }

    fun getMangaViewer(): Int {
        return manga?.viewer ?: 0
    }
}