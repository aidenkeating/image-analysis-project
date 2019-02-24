package com.aidenkeating.imageanalysis;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import com.aidenkeating.imageanalysis.image.BinaryImageFactory;
import com.aidenkeating.imageanalysis.image.FirstLastPixelImageGrouping;
import com.aidenkeating.imageanalysis.image.GrayscaleBinaryImageFactory;
import com.aidenkeating.imageanalysis.image.ImageGrouping;
import com.aidenkeating.imageanalysis.image.ImageUtil;
import com.aidenkeating.imageanalysis.util.QuickUnionFind;

/**
 * ImageAnalyzer is the main class a client should interact with. They should
 * not be invoking various components of the analysis process independently.
 * Instead, this class provides one function which the end-user can invoke to
 * produce an image with outlined distinct objects.
 * 
 * As the amount of parameters we take is this class are numerous and may
 * expand over time, we use the builder pattern to help the end-user keep a
 * clean code base and also make our own lives easier.
 * 
 * We could have also provided a "config object" as a single parameter if we
 * wanted.
 * @author aidenkeating
 *
 */
public class ImageAnalyzer {
	private final BinaryImageFactory binaryImageFactory;
	private final Color outlineColor;
	private final int noiseReduction;
	// This is nullable, when null do not resize the image.
	private final Dimension resizeDimension;
	
	public ImageAnalyzer(final BinaryImageFactory binaryImageFactory, final Color outlineColor, final int noiseReduction, final Dimension resizeDimension) {
		this.binaryImageFactory = binaryImageFactory;
		this.outlineColor = outlineColor;
		this.noiseReduction = (noiseReduction > 1 ? noiseReduction : 1);
		this.resizeDimension = resizeDimension;
	}
	
	/**
	 * Locates distinct objects in a provided image and produces a new image
	 * with them outlined.
	 * 
	 * @param image The image to analyze
	 * @return A scaled copy of the provided image
	 */
	public BufferedImage outlineDistinctObjects(final BufferedImage image) {
		// If resizeImage is not set then use the original image.
		final BufferedImage resizedImage = (this.resizeDimension != null ? ImageUtil.scaleImage(image, this.resizeDimension) : image);
		// Produce an image with only two distinct colors, black and white.
		final BufferedImage binaryImage = this.binaryImageFactory.produceBinaryImage(resizedImage);
		// Retrieve a list of object groupings from the binary image.
		final List<ImageGrouping> groupings = findDistinctObjectsInImage(binaryImage);
		// Outline the groupings in the original color image and return it.
		for(final ImageGrouping grouping : groupings) {
			grouping.applyToImage(resizedImage, this.outlineColor);
		}
		return resizedImage;
	}
	
	/**
	 * Find all sets of grouped pixels with the primary color in a binary image
	 * 
	 * This is where the UnionFind comes into play.
	 * 
	 * @param image The image to find groups in
	 * @return A list of groups
	 */
	private List<ImageGrouping> findDistinctObjectsInImage(final BufferedImage image) {
		// Initialize a QuickUnionFind struct. This is used to track which
		// pixels are connected to which.
		QuickUnionFind uf = new QuickUnionFind(image.getWidth()*image.getHeight());
		// Retrieve the raster for the image.
		//WritableRaster raster = image.getRaster();
		// Iterate through every pixel of the image, starting from the
		// upper-left.
		for(int row = 0; row < image.getHeight(); row++) {
			for(int col = 0; col < image.getWidth(); col++) {
				// Store some commonly used information about the pixel.
				final int pixelRGB = image.getRGB(col, row);
				final int nCols = image.getWidth();
				final int nRows = image.getHeight();
				final int pixelId = getId(row, col, nCols);
				
				// We only care about black pixels in this binary image.
				if (pixelRGB != this.binaryImageFactory.primaryColor().getRGB()) {
					continue;
				}
				// Check left.
				if (col > 0 && pixelRGB == image.getRGB(col - 1, row)) {
					uf.union(pixelId, getId(row, col - 1, nCols));
				}
				// Check right.
				if (col < nCols - 1 && pixelRGB == image.getRGB(col + 1, row)) {
					uf.union(pixelId, getId(row, col + 1, nCols));
				}
				// Check up.
				if (row > 0 && pixelRGB == image.getRGB(col, row - 1)) {
					uf.union(pixelId, getId(row - 1, col, nCols));
				}
				// Check down.
				if (row < nRows - 1 && pixelRGB == image.getRGB(col, row + 1)) {
					uf.union(pixelId, getId(row + 1, col, nCols));
				}
				// Check SE.
				if (row < nRows - 1 && col < nCols - 1 && pixelRGB == image.getRGB(col + 1, row + 1)) {
					uf.union(pixelId, getId(row + 1, col + 1, nCols));
				}
				// Check NW.
				if (row > 0 && col > 0 && pixelRGB == image.getRGB(col - 1, row - 1)) {
					uf.union(pixelId, getId(row - 1, col - 1, nCols));
				}
				// Check SW.
				if (row > 0 && col < nCols - 1 && pixelRGB == image.getRGB(col + 1, row - 1)) {
					uf.union(pixelId, getId(row - 1, col + 1, nCols));
				}
				// Check NE.
				if (row < nRows - 1 && col > 0 && pixelRGB == image.getRGB(col - 1, row + 1)) {
					uf.union(pixelId, getId(row + 1, col - 1, nCols));
				}
			}
		}
		
		List<ImageGrouping> imageGroupings = new ArrayList<ImageGrouping>();
		for(int root: uf.getRoots(this.noiseReduction)) {
			final List<Integer> treeElements = uf.getElementsOfTree(root);
			final int firstNode = treeElements.get(0);
			final int lastNode = treeElements.get(treeElements.size() - 1);

			// Get the coordinates for the first and last pixel in the tree.
			// These will be the first and last encountered, giving us a rough
			// set of coordinates to draw a box.
			final int x1 = firstNode%image.getWidth();
			final int y1 = firstNode/image.getWidth();
			final int x2 = lastNode%image.getWidth();
			final int y2 = lastNode/image.getWidth();
			
			imageGroupings.add(new FirstLastPixelImageGrouping(x1, y1, x2, y2));
		}
		
		return imageGroupings;
	}

	// Generated.
	public BinaryImageFactory getBinaryImageFactory() {
		return binaryImageFactory;
	}

	// Generated.
	public Color getOutlineColor() {
		return outlineColor;
	}

	// Generated.
	public int getNoiseReduction() {
		return noiseReduction;
	}

	// Generated.
	public Dimension getResizeDimension() {
		return resizeDimension;
	}

	// Utility function for retrieving the correct array position in a
	// QuickUnionFind for a specific pixel in an image with nCols columns.
	private static int getId(final int row, final int col, final int nCols) {
		return row * nCols + col;
	}
	
	/**
	 * Builder for ImageAnalyzer (Builder pattern).
	 * @author aidenkeating
	 */
	public static class Builder {
		private BinaryImageFactory binaryImageFactory;
		private Color outlineColor;
		private int noiseReduction;
		private Dimension resizeDimension;
		
		public Builder() {
			this.binaryImageFactory = new GrayscaleBinaryImageFactory(130);
			this.outlineColor = Color.BLUE;
			this.noiseReduction = 1;
			// When set to null, images will not be resized before they are
			// analyzed.
			this.resizeDimension = null;
		}
		
		public Builder withBinaryImageFactory(final BinaryImageFactory factory) {
			this.binaryImageFactory = factory;
			return this;
		}
		
		public Builder withOutlineColor(final Color outlineColor) {
			this.outlineColor = outlineColor;
			return this;
		}
		
		public Builder withNoiseReduction(final int noiseReduction) {
			this.noiseReduction = noiseReduction;
			return this;
		}
		
		public Builder withResizeDimension(final Dimension resizeDimension) {
			this.resizeDimension = resizeDimension;
			return this;
		}
		
		public ImageAnalyzer build() {
			return new ImageAnalyzer(this.binaryImageFactory, this.outlineColor, this.noiseReduction, this.resizeDimension);
		}
	}
}
