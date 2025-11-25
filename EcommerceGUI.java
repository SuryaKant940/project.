/* EcommerceGUI.java
   Self-contained, compile-ready demo of a Java GUI E-Commerce Cart system.
   - OOP: classes, interface (CartOperations)
   - Collections & Generics: List, Map, synchronized access
   - Multithreading: ProductLoaderThread updates GUI via SwingUtilities.invokeLater
   - Thread-safety: synchronized cart operations
   - NOTE: This demo uses a sample (in-memory) ProductDAO so no DB is required.
     To add JDBC, replace ProductDAO.getProducts() with JDBC code and ensure
     the JDBC driver is on the classpath.
*/

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/* --------------------- Product class (POJO) --------------------- */
class Product {
    private final int id;
    private final String name;
    private final double price;

    public Product(int id, String name, double price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }
    public int getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }

    @Override
    public String toString() {
        return name + " - $" + String.format("%.2f", price);
    }
}

/* --------------------- Cart operations interface --------------------- */
interface CartOperations<T> {
    void addItem(T item);
    void removeItem(T item);
    double getTotal();
}

/* --------------------- Thread-safe ShoppingCart --------------------- */
class ShoppingCart implements CartOperations<Product> {
    // synchronizedMap to demonstrate threadsafe collection + we also synchronize on methods
    private final Map<Product, Integer> cart = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public synchronized void addItem(Product item) {
        cart.put(item, cart.getOrDefault(item, 0) + 1);
    }

    @Override
    public synchronized void removeItem(Product item) {
        cart.remove(item);
    }

    @Override
    public synchronized double getTotal() {
        double sum = 0.0;
        for (Map.Entry<Product, Integer> e : cart.entrySet()) {
            sum += e.getKey().getPrice() * e.getValue();
        }
        return sum;
    }

    public synchronized Map<Product,Integer> snapshot() {
        return new LinkedHashMap<>(cart);
    }

    public synchronized void clear() {
        cart.clear();
    }
}

/* --------------------- ProductDAO (sample, in-memory) ---------------------
   Replace this class's getProducts() implementation with JDBC code when ready.
   Example JDBC steps:
     - Connection conn = DriverManager.getConnection(...);
     - PreparedStatement ps = conn.prepareStatement("SELECT id,name,price FROM products");
     - ResultSet rs = ps.executeQuery(); map rows to Product objects.
-------------------------------------------------------------------------- */
class ProductDAO {
    public List<Product> getProducts() {
        // sample products - in a real app you'd query the DB
        List<Product> list = new ArrayList<>();
        list.add(new Product(1, "Blue T-Shirt", 19.99));
        list.add(new Product(2, "Notebook", 6.50));
        list.add(new Product(3, "Coffee Mug", 12.75));
        list.add(new Product(4, "Headphones", 45.00));
        return list;
    }
}

/* --------------------- Background loader thread --------------------- */
class ProductLoaderThread extends Thread {
    private final ProductDAO dao;
    private final DefaultListModel<Product> model;
    private final List<Product> productList;

    public ProductLoaderThread(ProductDAO dao, DefaultListModel<Product> model, List<Product> productList) {
        this.dao = dao;
        this.model = model;
        this.productList = productList;
    }

    @Override
    public void run() {
        // Simulate longer load (e.g., DB/network)
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}

        List<Product> loaded = dao.getProducts();

        // update the shared product list (not GUI)
        synchronized (productList) {
            productList.clear();
            productList.addAll(loaded);
        }

        // GUI update must be done on EDT
        SwingUtilities.invokeLater(() -> {
            model.clear();
            for (Product p : loaded) model.addElement(p);
        });
    }
}

/* --------------------- Main GUI class --------------------- */
public class EcommerceGUI {
    private final JFrame frame;
    private final DefaultListModel<Product> listModel = new DefaultListModel<>();
    private final JList<Product> productJList = new JList<>(listModel);
    private final List<Product> products = Collections.synchronizedList(new ArrayList<>());
    private final ShoppingCart cart = new ShoppingCart();
    private final ProductDAO productDAO = new ProductDAO();

    public EcommerceGUI() {
        frame = new JFrame("E-Commerce Cart System (Demo)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 450);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(8,8));

        // Left panel: products
        JPanel left = new JPanel(new BorderLayout(6,6));
        left.setBorder(BorderFactory.createTitledBorder("Products"));
        productJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        left.add(new JScrollPane(productJList), BorderLayout.CENTER);

        JButton btnAddToCart = new JButton("Add to Cart");
        JButton btnReload = new JButton("Reload Products");
        JPanel leftButtons = new JPanel(new GridLayout(1,2,6,6));
        leftButtons.add(btnAddToCart);
        leftButtons.add(btnReload);
        left.add(leftButtons, BorderLayout.SOUTH);

        // Right panel: cart and actions
        JPanel right = new JPanel(new BorderLayout(6,6));
        right.setBorder(BorderFactory.createTitledBorder("Cart"));
        JTextArea cartArea = new JTextArea();
        cartArea.setEditable(false);
        right.add(new JScrollPane(cartArea), BorderLayout.CENTER);

        JButton btnShowTotal = new JButton("Show Total");
        JButton btnClearCart = new JButton("Clear Cart");
        JPanel rightButtons = new JPanel(new GridLayout(1,2,6,6));
        rightButtons.add(btnShowTotal);
        rightButtons.add(btnClearCart);
        right.add(rightButtons, BorderLayout.SOUTH);

        // Top: add-product small form (demonstration)
        JPanel top = new JPanel(new GridLayout(1,5,6,6));
        top.setBorder(BorderFactory.createTitledBorder("Add Product (Demo)"));
        JTextField tfName = new JTextField();
        JTextField tfPrice = new JTextField();
        JButton btnAddProduct = new JButton("Add");
        top.add(new JLabel("Name:")); top.add(tfName);
        top.add(new JLabel("Price:")); top.add(tfPrice);
        top.add(btnAddProduct);

        frame.add(top, BorderLayout.NORTH);
        frame.add(left, BorderLayout.WEST);
        frame.add(right, BorderLayout.CENTER);

        // Button behavior
        btnReload.addActionListener(e -> loadProductsInBackground());
        btnAddToCart.addActionListener(e -> {
            Product sel = productJList.getSelectedValue();
            if (sel == null) {
                JOptionPane.showMessageDialog(frame, "Select a product first.");
                return;
            }
            // Add in a short background thread to demonstrate concurrency
            new Thread(() -> {
                cart.addItem(sel);
                SwingUtilities.invokeLater(() -> updateCartArea(cartArea));
            }).start();
        });

        btnShowTotal.addActionListener(e -> {
            double total = cart.getTotal();
            JOptionPane.showMessageDialog(frame, "Cart total: $" + String.format("%.2f", total));
        });

        btnClearCart.addActionListener(e -> {
            cart.clear();
            updateCartArea(cartArea);
        });

        btnAddProduct.addActionListener(e -> {
            String name = tfName.getText().trim();
            String priceText = tfPrice.getText().trim();
            if (name.isEmpty() || priceText.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Provide name and price.");
                return;
            }
            try {
                double price = Double.parseDouble(priceText);
                // In a real app you'd persist this via ProductDAO (JDBC). Here we just add to the in-memory list:
                Product p = new Product(newIdForProduct(), name, price);
                synchronized (products) { products.add(p); }
                listModel.addElement(p);
                tfName.setText(""); tfPrice.setText("");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Invalid price format.");
            }
        });

        // initial load
        loadProductsInBackground();

        frame.setVisible(true);
    }

    private int newIdForProduct() {
        synchronized (products) {
            return products.stream().mapToInt(Product::getId).max().orElse(0) + 1;
        }
    }

    private void loadProductsInBackground() {
        ProductLoaderThread loader = new ProductLoaderThread(productDAO, listModel, products);
        loader.start();
    }

    private void updateCartArea(JTextArea area) {
        StringBuilder sb = new StringBuilder();
        Map<Product,Integer> snap = cart.snapshot();
        for (Map.Entry<Product,Integer> e : snap.entrySet()) {
            sb.append(e.getKey().getName())
              .append(" x").append(e.getValue())
              .append(" = $").append(String.format("%.2f", e.getKey().getPrice() * e.getValue()))
              .append("\n");
        }
        sb.append("\nTotal: $").append(String.format("%.2f", cart.getTotal()));
        area.setText(sb.toString());
    }

    public static void main(String[] args) {
        // Launch UI on Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> new EcommerceGUI());
    }
}
