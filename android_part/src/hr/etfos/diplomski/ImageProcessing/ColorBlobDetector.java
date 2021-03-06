7package hr.etfos.diplomski.ImageProcessing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import android.util.Log;
import android.view.animation.BounceInterpolator;

public class ColorBlobDetector {
	
	private int maxContourPosition = 0;
	private int offset = 50;
    // lower i upper granica za HSV
    private Scalar mLowerBound;
    private Scalar mUpperBound;
    private Scalar mColorRadius; // Color radius for range checking in HSV color space
    
    // Minimum contour area in percent for contours filtering
    private double mMaxContourArea;
    
    private List<MatOfPoint> mContours;
    private List<MatOfPoint> contours;
    
    private Size ROI_SIZE;
    
    //Rects
    private Rect rect;
    private Rect globalRect;
    
    // Mats
    private Mat mRoi;
    private Mat mPyrDownMat;
    private Mat mHsvMat;
    private Mat mMask;
    private Mat mDilatedMask;
    private Mat mHierarchy;
    
    private MatOfPoint mBiggestContour;
    private MatOfPoint2f approxCurve;
    
    private Point centroid;
    
    public ColorBlobDetector()
    {
    	mLowerBound = new Scalar(0);
        mUpperBound = new Scalar(0);
		mColorRadius = new Scalar(25,50,50,0);
		
		mRoi = new Mat();
		mPyrDownMat = new Mat();
		mHsvMat = new Mat();
		mMask = new Mat();
		mDilatedMask = new Mat();
		mHierarchy = new Mat();
		
		mContours = new ArrayList<MatOfPoint>();
		contours = new ArrayList<MatOfPoint>();
		mBiggestContour = new MatOfPoint();

		centroid = new Point();
		
		approxCurve = new MatOfPoint2f();
		
	    ROI_SIZE = new Size(32, 32);
    }
    
    public void setColorRadius(Scalar radius) {
        mColorRadius = radius;
    }

    /**nakon �to se izracuna average vrijednost roia, za hue, sat i value pronalaze se min i max vrijednosti tako da se od hue radi +-25, dok sat i value +-50*/
    private void calculateUpperAndLowerBound(Scalar hsvColor) {
    	
        double minH = (hsvColor.val[0] >= mColorRadius.val[0]) ? hsvColor.val[0] - mColorRadius.val[0] : 0;
        double maxH = (hsvColor.val[0] + mColorRadius.val[0] <= 255) ? hsvColor.val[0] + mColorRadius.val[0] : 255;

        mLowerBound.val[0] = minH;
        mUpperBound.val[0] = maxH;

        mLowerBound.val[1] = hsvColor.val[1] - mColorRadius.val[1];
        mUpperBound.val[1] = hsvColor.val[1] + mColorRadius.val[1];

        mLowerBound.val[2] = hsvColor.val[2] - mColorRadius.val[2];
        mUpperBound.val[2] = hsvColor.val[2] + mColorRadius.val[2];

        mLowerBound.val[3] = 0;
        mUpperBound.val[3] = 255;
    }
    
    public void setHsvColor(Mat image, Rect roi) {
    	
    	Mat touchedRgb = image.submat(roi);
		Mat touchedHsv = new Mat();
		Scalar mBlobColorHsv = new Scalar(255);
        
		Imgproc.cvtColor(touchedRgb, touchedHsv, Imgproc.COLOR_RGB2HSV_FULL);

		// ra�una prosjecnu vrijednost za svaki kanal hsv slike
		//8*8 piskela je velik roi, zbrajanjem svih piksela i djeljenjem sa 64 dobije se prosjek
        mBlobColorHsv = Core.sumElems(touchedHsv);
        int pointCount = roi.width*roi.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;
        
        calculateUpperAndLowerBound(mBlobColorHsv);
        Imgproc.resize(touchedRgb, mRoi, ROI_SIZE);
        
        touchedHsv.release();
        touchedRgb.release();
    }

    /**Funkcija koja pronalazi najvecu konturu na slici rgbaImage
     * @param rgbaImage Mat na kojoj se pronalazi najveca kontura.
     * @return vraca -1 ako nema konture, odnosno vraca njezinu velicinu ako ima*/
    public double findMaxContour(Mat rgbaImage)
    {
    	mMaxContourArea = -1;

        Imgproc.pyrDown(rgbaImage, mPyrDownMat); 
        Imgproc.pyrDown(mPyrDownMat, mPyrDownMat); 
        Imgproc.cvtColor(mPyrDownMat, mHsvMat, Imgproc.COLOR_RGB2HSV_FULL);

        Core.inRange(mHsvMat, mLowerBound, mUpperBound, mMask);
        Imgproc.dilate(mMask, mDilatedMask, new Mat());

        contours.clear();
        Imgproc.findContours(mDilatedMask, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE); //obrada
        
    	for(int i = 0; i < contours.size(); i++)
    	{
    		double area = Imgproc.contourArea(contours.get(i)); //obrada
    		
    		if(area > mMaxContourArea)
    		{
    			mMaxContourArea = area;
    			maxContourPosition = i;
    		}
    	}
    	
        return mMaxContourArea;
    }
       
    /**Pronalazi roi na zadanoj slici, sluzi za pronalazenje roia na frameu*/
    public void initRoi(Mat rgbaImage)
    {
    	if(contours.size() > 0)
    	{
	    	mBiggestContour = contours.get(maxContourPosition);
	    	Core.multiply(mBiggestContour, new Scalar(4,4), mBiggestContour);
	    	
	    	//koordinate konture u odnosu na cijeli frame.
	        Moments m = Imgproc.moments(mBiggestContour);
	        centroid.x =  (m.get_m10() / m.get_m00());
	        centroid.y = (m.get_m01() / m.get_m00());
	    	 	
	    	MatOfPoint2f contour2f = new MatOfPoint2f(mBiggestContour.toArray());
	    	double approxDistance = Imgproc.arcLength(contour2f, true)*0.01f;
	    	Imgproc.approxPolyDP(contour2f, approxCurve, approxDistance, true);
	    	
	    	//Rect koji opisuje kontoru
	    	globalRect = Imgproc.boundingRect(new MatOfPoint(approxCurve.toArray()));
	
	    	int newy = globalRect.y - offset < 0 ? 0 : globalRect.y - offset;
	    	int newx = globalRect.x - offset < 0 ? 0 : globalRect.x;
	    	int newheight = globalRect.y + globalRect.height + offset > rgbaImage.rows() ? globalRect.height : globalRect.height + offset;
	    	int newwidth = globalRect.x + globalRect.width + offset > rgbaImage.cols() ? globalRect.width : globalRect.width + offset;
	    	
	    	globalRect.x = newx;
	    	globalRect.y = newy;
	    	globalRect.width = newwidth;
	    	globalRect.height = newheight;
	    	mRoi = rgbaImage.submat(globalRect);
	    	    	
	    	contour2f.release();
    	}
    }
    
    public void updateRoi(Mat rgbaImage)
    {
    	mBiggestContour = contours.get(maxContourPosition);
    	Core.multiply(mBiggestContour, new Scalar(4,4), mBiggestContour);	
           	
    	//rect okolo konture
    	MatOfPoint2f contour2f = new MatOfPoint2f(mBiggestContour.toArray());
    	double approxDistance = Imgproc.arcLength(contour2f, true)*0.01f;
    	Imgproc.approxPolyDP(contour2f, approxCurve, approxDistance, true);
    	
    	//ima koordinate u odnosu na onaj pro�li roi.
    	rect = Imgproc.boundingRect(new MatOfPoint(approxCurve.toArray())); 

    	if(rect.x > offset + 10)
    		globalRect.x = globalRect.x + (rect.x - offset) < rgbaImage.cols() ? globalRect.x + (rect.x - offset) : globalRect.x;
    	else if(rect.x < offset - 10)
    		globalRect.x = globalRect.x - (offset - rect.x) > 0 ? globalRect.x - (offset - rect.x) : globalRect.x;
    	
    	if(rect.y > offset + 10)
    		globalRect.y = globalRect.y + (rect.y - offset) < rgbaImage.rows() ? globalRect.y + (rect.y - offset) : globalRect.y;
    	else if(rect.y < offset - 10)
    		globalRect.y = globalRect.y - (offset - rect.y) > 0 ? globalRect.y - (offset - rect.y) : globalRect.y;
    	
    	globalRect.width = globalRect.x + (2*offset + rect.width) < rgbaImage.cols() ? 2*offset + rect.width : (rgbaImage.cols() - globalRect.x);
    	globalRect.height = globalRect.y + (2*offset + rect.height) < rgbaImage.rows() ? 2*offset + rect.height : (rgbaImage.rows() - globalRect.y);
    	
    	mRoi = rgbaImage.submat(globalRect);
    	
        Moments m = Imgproc.moments(mBiggestContour); //obrada
        centroid.x = globalRect.x + (m.get_m10() / m.get_m00());
        centroid.y = globalRect.y + (m.get_m01() / m.get_m00());
    	
    	contour2f.release();
    }
       
    public void release()
    {
    	mRoi.release();
        mPyrDownMat.release();
        mHsvMat.release();
        mMask.release();
        mDilatedMask.release();
        mHierarchy.release();
        
        approxCurve.release();
        
        contours.clear();
        mContours.clear();
    }

    public double getMaxContourArea()
    {
    	return mMaxContourArea;
    }
    
    public Mat getRoi()
    {
    	return mRoi;	
    }
    
    public Point getCentroid()
    {
    	return centroid;
    }
    
    public MatOfPoint getBiggestContour()
    {
    	return mBiggestContour;
    }
    
    public Rect getRect()
    {
    	return rect;
    }
}
