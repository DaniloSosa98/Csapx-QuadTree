import com.sun.jdi.IntegerValue;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represents the Quadtree data structure used to compress raw
 * grayscale images and uncompress back.  Conceptually, the tree is
 * a collection of QTNode's.  A QTNode either holds a grayscale image
 * value (0-255), or QUAD_SPLIT, meaning the node is split into four
 * sub-nodes that are equally sized sub-regions that divide up the
 * current space.
 *
 * To learn more about quadtrees:
 *      https://en.wikipedia.org/wiki/Quadtree
 *
 * @author Sean Strout @ RIT
 * @author Danilo Sosa
 */
public class QTree {
    /** the value of a node that indicates it is split into 4 sub-regions */
    public final static int QUAD_SPLIT = -1;

    /** the root node in the tree */
    private QTNode root;

    /** the square dimension of the tree */
    private int DIM;

    /**  the raw image */
    private int image[][];

    /** the size of the raw image */
    private int rawSize;

    /** the size of the compressed image */
    private int compressedSize;

    /**
     * Create an initially empty tree.
     */
    public QTree() {
        this.root = null;
        this.DIM = 0;
        this.image = null;
        this.rawSize = 0;
        this.compressedSize = 0;
    }

    /**
     * Get the images square dimension.
     *
     * @return the square dimension
     */
    public int getDim() { return this.DIM; }

    /** Get the raw image.
     *
     * @return the raw image
     */
    public int[][] getImage(){ return this.image; }

    /**
     * Get the size of the raw image.
     *
     * @return raw image size
     */
    public int getRawSize() { return this.rawSize; }

    /**
     * Get the size of the compressed image.
     *
     * @return compressed image size
     */
    public int getCompressedSize() { return this.compressedSize; }

    /**
     * A private helper routine for parsing the compressed image into
     * a tree of nodes.  When parsing through the values, there are
     * two cases:
     *
     * 1. The value is a grayscale color (0-255).  In this case
     * return a node containing the value.
     *
     * 2. The value is QUAD_SPLIT.  The node must be split into
     * four sub-regions.  Each sub-region is attained by recursively
     * calling this routine.  A node containing these four sub-regions
     * is returned.
     *
     * @param values the values in the compressed image
     * @return a node that encapsulates this portion of the compressed
     * image
     * @throws QTException if there are not enough values in the
     * compressed image
     */
    //you gave us this method
    private QTNode parse(List<Integer> values) throws QTException {
        int val = values.remove(0);
        if(val != -1){
            return new QTNode(val);
        }else{
            QTNode ul = parse(values);
            QTNode ur = parse(values);
            QTNode ll = parse(values);
            QTNode lr = parse(values);
            return new QTNode(val, ul, ur, ll, lr);
        }
    }

    /**
     * This is the core routine for uncompressing an image stored in a tree
     * into its raw image (a 2-D array of grayscale values (0-255).
     * It is called by the public uncompress routine.
     * The main idea is that we are working with a tree whose root represents the
     * entire 2^n x 2^n image.  There are two cases:
     *
     * 1. The node is not split.  We can write out the corresponding
     * "block" of values into the raw image array based on the size
     * of the region
     *
     * 2. The node is split.  We must recursively call ourselves with the
     * the four sub-regions.  Take note of the pattern for representing the
     * starting coordinate of the four sub-regions of a 4x4 grid:
     *      - upper left: (0, 0)
     *      - upper right: (0, 1)
     *      - lower left: (1, 0)
     *      - lower right: (1, 1)
     * We can generalize this pattern by computing the offset and adding
     * it to the starting row and column in the appropriate places
     * (there is a 1).
     *
     * @param node the node to uncompress
     * @param size the size of the square region this node represents
     * @param start the starting coordinate this row represents in the image
     */
    private void uncompress(QTNode node, int size, Coordinate start) {
        //if value is a grayscale draw it on the whole region
        if(node.getVal() != -1){
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    this.getImage()[start.getRow() + i][start.getCol() +j] = node.getVal();
                }
            }
        //if not, recursive call through the tree with the required offset and size
        }else{
            uncompress(node.getUpperLeft(), (size/2), start);
            uncompress(node.getUpperRight(), (size/2), new Coordinate(start.getRow(), start.getCol() + (size/2)));
            uncompress(node.getLowerLeft(), (size/2), new Coordinate(start.getRow() + (size/2), start.getCol()));
            uncompress(node.getLowerRight(), (size/2), new Coordinate(start.getRow() + (size/2), start.getCol() + (size/2)));
        }
    }

    /**
     * Uncompress a RIT compressed file.  This is the public facing routine
     * meant to be used by a client to uncompress an image for displaying.
     *
     * The file is expected to be 2^n x 2^n pixels.  The first line in
     * the file is its size (number of values).  The remaining lines are
     * the values in the compressed image, one per line, of "size" lines.
     *
     * Once this routine completes, the raw image of grayscale values (0-255)
     * is stored internally and can be retrieved by the client using getImage().
     *
     * @param filename the name of the compressed file
     * @throws IOException if there are issues working with the compressed file
     * @throws QTException if there are issues parsing the data in the file
     */
    public void uncompress(String filename) throws IOException, QTException {
        //list for my read values
        List<Integer> pixels = new ArrayList();
        FileReader fr = new FileReader(filename);
        BufferedReader br = new BufferedReader(fr);

        String current;
        //go through the file and add them to the list
        while((current = br.readLine()) != null){
            int value = Integer.valueOf(current);
            pixels.add(value);
        }

        br.close();
        //define dimensions based on the first line of the file
        this.DIM = (int)Math.sqrt(Integer.valueOf(pixels.get(0)));
        //define dimensions of the image
        this.image = new int[getDim()][getDim()];
        //remove 'root' from compressed file
        pixels.remove(0);
        //begin the uncompressing process
        Coordinate start = new Coordinate(0,0);
        this.root = parse(pixels);
        uncompress(this.root, getDim(), start);

    }

    /**
     * The private writer is a recursive helper routine that writes out the
     * compressed image.  It goes through the tree in preorder fashion
     * writing out the values of each node as they are encountered.
     *
     * @param node the current node in the tree
     * @param writer the writer to write the node data out to
     * @throws IOException if there are issues with the writer
     */
    private void write(QTNode node, BufferedWriter writer) throws IOException {
        String values = preorder(this.root);
        String[] list = values.split(" ");
        for (int i = 0; i < list.length; i++) {
            writer.write(list[i] + "\n");
            this.compressedSize++;
        }

    }

    /**
     * Write the compressed image to the output file.  This routine is meant to be
     * called from a client after it has been compressed
     *
     * @rit.pre client has called compress() to compress the input file
     * @param outFile the name of the file to write the compressed image to
     * @throws IOException any errors involved with writing the file out
     * @throws QTException if the file has not been compressed yet
     */
    public void write(String outFile) throws IOException, QTException {
        FileWriter fw = new FileWriter(outFile);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(String.valueOf(this.getRawSize()) + " \n");
        write(this.root, bw);
        bw.close();
    }

    /**
     * Check to see whether a region in the raw image contains the same value.
     * This routine is used by the private compress routine so that it can
     * construct the nodes in the tree.
     *
     * @param start the starting coordinate in the region
     * @param size the size of the region
     * @return whether the region can be compressed or not
     */
    private boolean canCompressBlock(Coordinate start, int size) {
        int val = getImage()[start.getRow()][start.getCol()];
        //check if every value in the region is the same
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if(getImage()[start.getRow()+i][start.getCol()+j] != val){
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * This is the core compression routine.  Its job is to work over a region
     * of the image and compress it.  It is a recursive routine with two cases:
     *
     * 1. The entire region represented by this image has the same value, or
     * we are down to one pixel.  In either case, we can now create a node
     * that represents this.
     *
     * 2. If we can't compress at this level, we need to divide into 4
     * equally sized sub-regions and call ourselves again.  Just like with
     * uncompressing, we can compute the starting point of the four sub-regions
     * by using the starting point and size of the full region.
     *
     * @param start the start coordinate for this region
     * @param size the size this region represents
     * @return a node containing the compression information for the region
     */
    private QTNode compress(Coordinate start, int size) throws QTException{
        //if it can be compressed or size = 1, compress the region
        if(canCompressBlock(start, size) || size == 1){
            return new QTNode(getImage()[start.getRow()][start.getCol()]);
        //if not, recursive call until region can be compressed
        }else{
            return new QTNode(QUAD_SPLIT, compress(start, (size/2)),
                    compress(new Coordinate(start.getRow(), start.getCol() + (size/2)), (size/2)),
                    compress(new Coordinate (start.getRow() + (size/2) , start.getCol()) , (size/2)),
                    compress(new Coordinate(start.getRow() + (size/2) , start.getCol() + (size/2)), (size/2)));
        }
    }

    /**
     * Compress a raw image into the RIT format.  This routine is meant to be
     * called by a client.  It is expected to be passed a file which represents
     * the raw image.  It is ASCII formatted and contains a series of grayscale
     * values (0-255).  There is one value per line, and 2^n x 2^n total lines.
     *
     * @param inputFile the raw image file name
     * @throws IOException if there are issues working with the file
     */
    public void compress(String inputFile) throws IOException, QTException {
        List<Integer> pixels = new ArrayList();
        FileReader fr = new FileReader(inputFile);
        BufferedReader br = new BufferedReader(fr);

        String current;

        while((current = br.readLine()) != null){
            int value = Integer.valueOf(current);
            pixels.add(value);
        }

        br.close();
        //size from amount of lines
        this.rawSize = pixels.size();
        //dim is the square root of the size
        this.DIM = (int) Math.sqrt(this.getRawSize());
        //define image
        this.image = new int[getDim()][getDim()];
        //fill up image (I know it is not necessary but I was trying some things out)
        int index = 0;
        for (int i = 0; i < this.getDim(); i++) {
            for (int j = 0; j < this.getDim(); j++) {
                this.image[i][j] = pixels.get(index++);
            }
        }
        //begin compression process
        Coordinate start = new Coordinate(0,0);
        this.compressedSize++;
        this.root = this.compress(start, getDim());
    }

    /**
     * A preorder (parent, left, right) traversal of a node.  It returns
     * a string which is empty if the node is null.  Otherwise
     * it returns a string that concatenates the current node's value
     * with the values of the 4 sub-regions (with spaces between).
     *
     * @param node the node being traversed on
     * @return the string of the node
     */
    private String preorder(QTNode node) {
        if (node != null){
            return node.getVal() + " " + preorder(node.getUpperLeft()) +  preorder(node.getUpperRight())
                    + preorder(node.getLowerLeft()) + preorder(node.getLowerRight()) ;
        }
        return "";
    }

    /**
     * Returns a string which is a preorder traversal of the tree.
     *
     * @return the qtree string representation
     */
    @Override
    public String toString() {
        return "QTree: " + preorder(this.root);
    }
}