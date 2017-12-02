package course.labs.graphicslab;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.sip.SipAudioCall;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import static java.lang.Math.sqrt;

public class BubbleActivity extends Activity {

	// Эти переменные нужны для тестирования, не изменять
	private final static int RANDOM = 0;
	private final static int SINGLE = 1;
	private final static int STILL = 2;
	private static int speedMode = RANDOM;

	private static final String TAG = "Lab-Graphics";

	// Главный view
	private RelativeLayout mFrame;

	// Bitmap изображения пузыря
	private Bitmap mBitmap;

	// размеры экрана
	private int mDisplayWidth, mDisplayHeight;

	// Звуковые переменные

	// AudioManager
	private AudioManager mAudioManager;
	// SoundPool
	private SoundPool mSoundPool;
	// ID звука лопания пузыря 
	private int mSoundID;
	// Громкость аудио
	private float mStreamVolume;

	// Детектор жестов
	private GestureDetector mGestureDetector;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		// Установка пользовательского интерфейса
		mFrame = (RelativeLayout) findViewById(R.id.frame);

		// Загружаем базовое изображение для пузыря
		mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.b64);

	}

	@Override
	protected void onResume() {
		super.onResume();

		// Управляем звуком лопания пузыря
		// Используем AudioManager.STREAM_MUSIC в качестве типа потока

		mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

		mStreamVolume = (float) mAudioManager
				.getStreamVolume(AudioManager.STREAM_MUSIC)
				/ mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

		// TODO - создаем новый SoundPool, предоставляющий до 10 потоков 
		mSoundPool = new SoundPool(10,AudioManager.STREAM_MUSIC,10);

		// TODO - устанавливаем для SoundPool листенер OnLoadCompletedListener который вызывает setupGestureDetector()
		//new SoundPool.OnLoadCompleteListener(setupGestureDetector());
		mSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
			@Override
			public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
				setupGestureDetector();
			}
		});

		// TODO - загружаем звук из res/raw/bubble_pop.wav
		mSoundID = 	mSoundPool.load(this, R.raw.bubble_pop, 1);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {

			// Получаем размер экрана для того чтобы View знал, где границы отображения
			mDisplayWidth = mFrame.getWidth();
			mDisplayHeight = mFrame.getHeight();

		}
	}

	// Устанавливаем GestureDetector
	private void setupGestureDetector() {

		mGestureDetector = new GestureDetector(this,

		new GestureDetector.SimpleOnGestureListener() {

			// Если на BubleView происходит жест швыряния, тогда изменяем его направление и скорость (velocity)

			@Override
			public boolean onFling(MotionEvent event1, MotionEvent event2,
					float velocityX, float velocityY) {

				// TODO - Реализуйте onFling.
				// Вы можете получить все Views в mFrame используя
				// метод ViewGroup.getChildCount()
				for (int i =0;i<mFrame.getChildCount();i++){
					BubbleView bubble = (BubbleView) mFrame.getChildAt(i);
					if (bubble.intersects(event1.getRawX(),event1.getRawY())){
						bubble.deflect(velocityX,velocityY);
						return true;
					}
				}


				
				
				
				return false;

			}

			// Если простое нажатие попадает по BubbleView, тогда лопаем BubbleView
			// Иначе, создаем новый BubbleView в центре нажатия и добавляем его в
			// mFrame. Вы можете получить все компоненты в mFrame с помощью метода ViewGroup.getChildAt()

			@Override
			public boolean onSingleTapConfirmed(MotionEvent event) {

				// TODO - Реализуйте onSingleTapConfirmed.

				for(int i=0;i<mFrame.getChildCount();i++) {
					BubbleView nextChild = (BubbleView)mFrame.getChildAt(i);
					if (nextChild.intersects(event.getX(),event.getY()))
					{
						nextChild.stop(true);
						return false;
					}

				}
				mFrame.addView(new BubbleView(mFrame.getContext(),event.getX(),event.getY()));

				return false;
			}
		});
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {

		// TODO - Делегируем нажатие детектору жестов gestureDetector
		mGestureDetector.onTouchEvent(event);

		return false;
	
	}

	@Override
	protected void onPause() {
		
		// TODO - Освобождаем все ресурсы пула SoundPool
		mSoundPool.release();
		super.onPause();
	}

	// BubbleView это View который отображает пузырь.
	// Этот класс управляет анимацией, отрисовкой и лопанием.
	// Новый BubbleView создается отдельно для каждого пузыря на экране

	public class BubbleView extends View {

		private static final int BITMAP_SIZE = 64;
		private static final int REFRESH_RATE = 40;
		private final Paint mPainter = new Paint();
		private ScheduledFuture<?> mMoverFuture;
		private int mScaledBitmapWidth;
		private Bitmap mScaledBitmap;

		// местоположение, скорость и направление пузыря
		private float mXPos, mYPos, mDx, mDy, mRadius, mRadiusSquared;
		private long mRotate, mDRotate;

		BubbleView(Context context, float x, float y) {
			super(context);

			// Создае новое генератор случайных чисел для рандомизации
			// размера, вращения, скорости и направления
			Random r = new Random();

			// Создает битмэп изображения пузыря для этого BubbleView
			createScaledBitmap(r);

			// Радиус Bitmap
			mRadius = mScaledBitmapWidth / 2;
			mRadiusSquared = mRadius * mRadius;

			// Центрируем положение пузыря относительно точки касания пальца пользователя
			mXPos = x - mRadius;
			mYPos = y - mRadius;

			// Устанавливаем скорость и направление BubbleView
			setSpeedAndDirection(r);
			
			// Устанавливаем вращение BubbleView
			setRotation(r);

			mPainter.setAntiAlias(true);
			start();
		}

		private void setRotation(Random r) {

			if (speedMode == RANDOM) {
				mDRotate = r.nextInt(3)+1;
				// TODO - установить вращение в диапазоне [1..3]
				//mDRotate = 0;


			} else {
				mDRotate = 0;
			
			}
		}

		private void setSpeedAndDirection(Random r) {

			// Используется тестами
			switch (speedMode) {

			case SINGLE:

				mDx = 20;
				mDy = 20;
				break;

			case STILL:

				// Нулевая скорость
				mDx = 0;
				mDy = 0;
				break;

			default:

				// TODO - Устанавливаем направление движения и скорость
				// Ограничиваем скорость движения по x и y
				// в диапазоне [-3..3] пикселя на движение.
				mDx = r.nextFloat() * 6.0f - 3.0f;
				mDy = r.nextFloat() * 6.0f - 3.0f;

			}
		}

		private void createScaledBitmap(Random r) {

			if (speedMode != RANDOM) {
				mScaledBitmapWidth = BITMAP_SIZE * 3;
			
			} else {
				//TODO - устанавливаем масштабирование размера изображения в диапазоне [1..3] * BITMAP_SIZE
				mScaledBitmapWidth = r.nextInt(3)+1*BITMAP_SIZE;
			
			}

			// TODO - Создаем масштабированное изображение, используя размеры, установленные выше.
			mScaledBitmap = Bitmap.createScaledBitmap(mBitmap, mScaledBitmapWidth, mScaledBitmapWidth, true);

		}

		// Начинаем перемещать BubbleView & обновлять экран
		private void start() {

			// Создаем WorkerThread
			ScheduledExecutorService executor = Executors
					.newScheduledThreadPool(1);

			// Запускаем run() в Worker Thread каждые REFRESH_RATE милисекунд
			// Сохраняем ссылку на данный процесс в mMoverFuture
			mMoverFuture = executor.scheduleWithFixedDelay(new Runnable() {
				@Override
				public void run() {

					// TODO - реализуйте логику движения.
					// Каждый раз, когда данный метод запускается, BubbleView должен
					// сдвинуться на один шаг. Если BubbleView покидает экран, 
					// останавливаем Рабочий Поток для BubbleView.
					// В противном случае запрашиваем перерисовку BubbleView. 
					if (moveWhileOnScreen()) {
						stop (false);
					} else {
						CheckBubbles();
						BubbleView.this.postInvalidate();
					}


				}
			}, 0, REFRESH_RATE, TimeUnit.MILLISECONDS);
		}

		private void CheckBubbles()
		{
			double curX = BubbleView.this.mXPos;
			double curY = BubbleView.this.mYPos;
			double radius = BubbleView.this.mRadius;
			if(mFrame.getChildCount()>0) {
				for (int i = 0; i < mFrame.getChildCount(); i++) {
					BubbleView Bubble2 = (BubbleView) mFrame.getChildAt(i);
					if(curX!= Bubble2.mXPos && curY!= Bubble2.mYPos) {
						double d = ((curX - Bubble2.mXPos) * (curX - Bubble2.mXPos)) + ((curY - Bubble2.mYPos) * (curY - Bubble2.mYPos));
						double sr = radius + Bubble2.mRadius;
						if (sr*sr >= d && d+radius > Bubble2.mRadius && d+Bubble2.mRadius > radius) {
							BubbleView.this.stop(false);
							Bubble2.stop(false);
						}
					}
				}
			}
		}

		// Возвращает истину, если BubbleView пересекает точку (x,y)
		private synchronized boolean intersects(float x, float y) {

			// TODO - Вернуть true если BubbleView пересекает точку (x,y)
			if ((mXPos <= x) && (x <= mXPos + mScaledBitmapWidth) && (mYPos <= y) && (y <= mYPos + mScaledBitmapWidth)) {
				return true;
			}
			return false;

		}

		// Отменяем движение Пузыря
		// Удаляем Пузырь с mFrame
		// Играем звук лопания, если BubbleView лопнули

		private void stop(final boolean wasPopped) {

			if (null != mMoverFuture && !mMoverFuture.isDone()) {
				mMoverFuture.cancel(true);
			}

			// Данный код будет выполнен в UI потоке
			mFrame.post(new Runnable() {
				@Override
				public void run() {

					// TODO - Удаляем BubbleView из mFrame
					//mFrame.removeView(BubbleView);
					mFrame.removeView(BubbleView.this);
					// TODO - Если пузырь лопнут пользователем,
					// играем звук лопания
					if (wasPopped) {
						mSoundPool.play(mSoundID, mStreamVolume, mStreamVolume, 1, 0, 1f);

					}
				}
			});
		}

		// Изменяем скорость и направление Пузыря.
		private synchronized void deflect(float velocityX, float velocityY) {

			//TODO - установить mDx и mDy в качестве новых значений velocity, разделив их на REFRESH_RATE
			mDx = velocityX / REFRESH_RATE;
			mDy = velocityY / REFRESH_RATE;


		}

		// Рисуем Пузырь в его текущем положении
		@Override
		protected synchronized void onDraw(Canvas canvas) {

			// TODO - сохраняем canvas
			canvas.save();

			
			// TODO - Увеличиваем вращение исходного изображения на mDRotate
			mRotate += mDRotate;
			canvas.rotate(mRotate, mXPos + (mScaledBitmapWidth / 2), mYPos + (mScaledBitmapWidth / 2));
			canvas.drawBitmap(mScaledBitmap, mXPos, mYPos, mPainter);
			canvas.restore();

			
		}

		// Возвращает true если BubbleView все еще на экране после хода
		// operation
		private synchronized boolean moveWhileOnScreen() {

			// TODO - Перемещаем BubbleView

			mXPos += mDx;
			mYPos += mDy;
			return isOutOfView();
		}

		// Возвращаем true, если BubbleView ушел с экрана после завершения хода
		private boolean isOutOfView() {

			// TODO - Возвращаем true, если BubbleView вне экрана после завершения хода
			if ((mXPos-10< 0)
					|| (mYPos-10 < 0)
					|| (mXPos+20> mDisplayWidth)
					|| (mYPos+20 > mDisplayHeight))
			{
				//return true;
				mDx*=-1;
				mDy*=-1;
			}

			return false;
		}
	}

	// Не изменяйте следующий код

	@Override
	public void onBackPressed() {
		openOptionsMenu();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.menu, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_still_mode:
			speedMode = STILL;
			return true;
		case R.id.menu_single_speed:
			speedMode = SINGLE;
			return true;
		case R.id.menu_random_mode:
			speedMode = RANDOM;
			return true;
		case R.id.quit:
			exitRequested();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void exitRequested() {
		super.onBackPressed();
	}
}