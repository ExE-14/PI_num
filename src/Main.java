import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.InputMismatchException;
import java.util.Scanner;
import org.apfloat.Apfloat;
import org.apfloat.ApfloatMath;

public class Main {

    private static MathContext MC = new MathContext(100, RoundingMode.HALF_UP);
    private static final int LINE_WIDTH = 80;
    private static int linePos = 0;

    private static final Object FILE_LOCK = new Object();
    private static final StringBuilder log = new StringBuilder();
    private static String currentMethod = "log";

    private static void printWrapped(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            System.out.print(c);
            synchronized (log) { log.append(c); }
            if (c == '\n') {
                linePos = 0;
            } else {
                linePos++;
                if (linePos >= LINE_WIDTH) {
                    System.out.println();
                    synchronized (log) { log.append('\n'); }
                    linePos = 0;
                }
            }
            System.out.flush();
        }
    }

    private static void writeLogNow() {
        synchronized (FILE_LOCK) {
            try (FileWriter writer = new FileWriter(currentMethod + ".txt")) {
                synchronized (log) { writer.write(log.toString()); }
                System.out.println("\nРезультат записан в " + currentMethod + ".txt");
            } catch (IOException e) {
                System.err.println("Ошибка записи в файл: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { writeLogNow(); } catch (Throwable ignored) {}
        }));

        Scanner scanner = new Scanner(System.in);

        mainLoop:
        while (true) {
            try {
                System.out.println("\nВыберите метод вычисления π:");
                System.out.println("1. Формула Мэчина");
                System.out.println("2. Ряд Нилаканты");
                System.out.println("3. Спиготовый алгоритм (Rabinowitz–Wagon)");
                System.out.println("4. Библиотека Apfloat");
                System.out.println("5. Извлечь отдельную цифру (BBP, hex/dec)");
                System.out.println("0. Выход");
                System.out.print("Ваш выбор: ");

                int choice;
                try {
                    choice = scanner.nextInt();
                    scanner.nextLine();
                } catch (InputMismatchException ime) {
                    scanner.nextLine();
                    System.out.println("Неверный ввод. Введите число от 0 до 5.");
                    continue;
                }

                if (choice == 0) {
                    System.out.println("Завершение программы.");
                    break;
                }

                long maxDigits = -1;
                int apfloatDigits = 0;

                if (choice >= 1 && choice <= 3) {
                    System.out.print("Вывести бесконечно? (Y/N): ");
                    String yn = scanner.nextLine().trim();
                    if (yn.equalsIgnoreCase("N") || yn.equalsIgnoreCase("NO")) {
                        System.out.print("Сколько цифр после запятой вывести (натуральное число): ");
                        try {
                            long v = Long.parseLong(scanner.nextLine().trim());
                            if (v > 0) maxDigits = v;
                            else { System.out.println("Неверное число — будет режим бесконечно."); maxDigits = -1; }
                        } catch (Exception e) {
                            System.out.println("Неверный ввод — будет режим бесконечно.");
                            maxDigits = -1;
                        }
                    } else {
                        maxDigits = -1;
                    }
                } else if (choice == 4) {
                    System.out.print("Сколько цифр π посчитать после запятой (например, 1000): ");
                    try {
                        apfloatDigits = Integer.parseInt(scanner.nextLine().trim());
                    } catch (Exception e) {
                        System.out.println("Неверный ввод числа. По умолчанию 5000.");
                        apfloatDigits = 5000;
                    }
                    if (apfloatDigits <= 0) apfloatDigits = 5000;
                }

                switch (choice) {
                    case 1:
                        currentMethod = "machin";
                        synchronized (log) { log.setLength(0); }
                        try { calculateWithMachin(maxDigits); } catch (Throwable t) { System.err.println("Ошибка Machin: " + t.getMessage()); t.printStackTrace(); writeLogNow(); }
                        break;
                    case 2:
                        currentMethod = "nilakantha";
                        synchronized (log) { log.setLength(0); }
                        try { calculateWithNilakantha(maxDigits); } catch (Throwable t) { System.err.println("Ошибка Nilakantha: " + t.getMessage()); t.printStackTrace(); writeLogNow(); }
                        break;
                    case 3:
                        currentMethod = "spigot";
                        synchronized (log) { log.setLength(0); }
                        try { calculateWithSpigot(maxDigits); } catch (Throwable t) { System.err.println("Ошибка Spigot: " + t.getMessage()); t.printStackTrace(); writeLogNow(); }
                        break;
                    case 4:
                        currentMethod = "apfloat";
                        synchronized (log) { log.setLength(0); }
                        try { calculateWithApfloat(apfloatDigits); } catch (Throwable t) { System.err.println("Ошибка Apfloat: " + t.getMessage()); t.printStackTrace(); writeLogNow(); }
                        break;
                    case 5:
                        try { extractIsolatedDigit(scanner); } catch (Throwable t) { System.err.println("Ошибка извлечения: " + t.getMessage()); t.printStackTrace(); writeLogNow(); }
                        break;
                    default:
                        System.out.println("Неверный выбор. Попробуйте ещё раз.");
                }
            } catch (Exception e) {
                System.err.println("Непредвиденная ошибка: " + e.getMessage());
                e.printStackTrace();
                writeLogNow();
                break mainLoop;
            }
        }

        scanner.close();
        writeLogNow();
    }

    // Machin
    private static void calculateWithMachin(long maxDigits) throws InterruptedException {
        printWrapped("π = 3");
        BigDecimal pi;
        long printed = 0;
        while (maxDigits <= 0 || printed < maxDigits) {
            int internalPrec = Math.max(20, (int)Math.min(Integer.MAX_VALUE - 10, printed * 10 + 10));
            MC = new MathContext(internalPrec, RoundingMode.HALF_UP);

            BigDecimal term1 = arctan(BigDecimal.ONE.divide(BigDecimal.valueOf(5), MC), MC);
            BigDecimal term2 = arctan(BigDecimal.ONE.divide(BigDecimal.valueOf(239), MC), MC);
            pi = term1.multiply(BigDecimal.valueOf(4), MC).subtract(term2, MC).multiply(BigDecimal.valueOf(4), MC);

            String s = pi.round(new MathContext(Math.max(5, (int)printed + 5), RoundingMode.DOWN)).toString();
            if (s.startsWith("3.")) s = s.substring(2);
            else if (s.contains(".")) s = s.substring(s.indexOf('.') + 1);

            if (s.length() > printed) {
                String newDigits = s.substring((int)printed);
                if (maxDigits > 0 && printed + newDigits.length() > maxDigits) {
                    newDigits = newDigits.substring(0, (int)(maxDigits - printed));
                }
                printWrapped(newDigits);
                printed += newDigits.length();
            }

            if (maxDigits > 0 && printed >= maxDigits) break;
            Thread.sleep(100);
        }
        printWrapped("\n[Machin завершил вывод]\n");
        writeLogNow();
    }

    // Nilakantha
    private static void calculateWithNilakantha(long maxDigits) throws InterruptedException {
        printWrapped("π = 3");
        BigDecimal pi = BigDecimal.valueOf(3);
        BigDecimal n = BigDecimal.valueOf(2);
        int sign = 1;
        long printed = 0;
        while (maxDigits <= 0 || printed < maxDigits) {
            BigDecimal term = BigDecimal.valueOf(4)
                    .divide(n.multiply(n.add(BigDecimal.ONE)).multiply(n.add(BigDecimal.valueOf(2))), MC);
            if (sign > 0) pi = pi.add(term, MC);
            else pi = pi.subtract(term, MC);
            sign *= -1;
            n = n.add(BigDecimal.valueOf(2));

            String s = pi.toString();
            if (s.contains(".")) {
                String afterDecimal = s.substring(s.indexOf('.') + 1);
                if (afterDecimal.length() > 0) {
                    char d = afterDecimal.charAt(afterDecimal.length() - 1);
                    printWrapped(String.valueOf(d));
                    printed++;
                }
            }

            if (maxDigits > 0 && printed >= maxDigits) break;
            Thread.sleep(100);
        }
        printWrapped("\n[Nilakantha завершил вывод]\n");
        writeLogNow();
    }

    // Spigot
    private static void calculateWithSpigot(long maxDigits) {
        printWrapped("π = ");
        BigInteger q = BigInteger.ONE, r = BigInteger.ZERO, t = BigInteger.ONE;
        BigInteger k = BigInteger.ONE, n = BigInteger.valueOf(3), l = BigInteger.valueOf(3);
        boolean first = true;
        long printed = 0;
        while (maxDigits <= 0 || printed < maxDigits) {
            if (BigInteger.valueOf(4).multiply(q).add(r).subtract(t).compareTo(n.multiply(t)) < 0) {
                String out = n.toString();
                printWrapped(out);
                if (first) { printWrapped("."); first = false; }
                printed += out.length();

                BigInteger nr = BigInteger.TEN.multiply(r.subtract(n.multiply(t)));
                n = BigInteger.TEN.multiply(BigInteger.valueOf(3).multiply(q).add(r)).divide(t).subtract(n.multiply(BigInteger.TEN));
                q = q.multiply(BigInteger.TEN);
                r = nr;
                System.out.flush();
            } else {
                BigInteger nr = BigInteger.TWO.multiply(q).add(r).multiply(l);
                BigInteger nn = q.multiply(BigInteger.valueOf(7).multiply(k)).add(BigInteger.valueOf(2)).add(r.multiply(l))
                        .divide(t.multiply(l));
                q = q.multiply(k);
                t = t.multiply(l);
                l = l.add(BigInteger.TWO);
                k = k.add(BigInteger.ONE);
                n = nn;
                r = nr;
            }
        }
        printWrapped("\n[Spigot завершил вывод]\n");
        writeLogNow();
    }

    // Apfloat
    private static void calculateWithApfloat(int digits) {
        printWrapped("π = ");
        try {
            Apfloat pi = ApfloatMath.pi(digits);
            String s = pi.toString(true);
            for (char c : s.toCharArray()) {
                printWrapped(String.valueOf(c));
                try { Thread.sleep(20); } catch (InterruptedException ignored) {}
            }
        } catch (NoClassDefFoundError e) {
            System.out.println("Библиотека Apfloat не найдена в classpath.");
        } catch (OutOfMemoryError oom) {
            System.err.println("Недостаточно памяти для Apfloat с таким количеством цифр.");
        } catch (Exception e) {
            System.err.println("Ошибка Apfloat: " + e.getMessage());
            e.printStackTrace();
        }
        printWrapped("\n[Apfloat завершил вывод]\n");
        writeLogNow();
    }

    private static BigDecimal arctan(BigDecimal x, MathContext mc) {
        BigDecimal result = BigDecimal.ZERO;
        BigDecimal power = x;
        BigDecimal x2 = x.multiply(x, mc);
        for (int i = 0; i < mc.getPrecision(); i++) {
            BigDecimal term = power.divide(BigDecimal.valueOf(2L * i + 1L), mc);
            if ((i & 1) == 0) result = result.add(term, mc);
            else result = result.subtract(term, mc);
            power = power.multiply(x2, mc);
        }
        return result;
    }

    // Extract isolated digit
    private static void extractIsolatedDigit(Scanner sc) {
        System.out.print("Позиция цифры (1 = первая цифра после запятой): ");
        long pos;
        try {
            pos = sc.nextLong();
            sc.nextLine();
        } catch (InputMismatchException ime) {
            sc.nextLine();
            System.out.println("Неверный ввод позиции.");
            return;
        }
        if (pos <= 0) {
            System.out.println("Позиция должна быть положительной.");
            return;
        }

        System.out.print("База (16 для hex, 10 для dec): ");
        int base;
        try {
            base = sc.nextInt();
            sc.nextLine();
        } catch (InputMismatchException ime) {
            sc.nextLine();
            System.out.println("Неверная база. Допустимо 16 или 10.");
            return;
        }

        if (base == 16) {
            System.out.println("Запрос: hex-цифра на позиции " + pos);
            long t0 = System.currentTimeMillis();
            int hex = bbpHexDigit(pos);
            long t1 = System.currentTimeMillis();
            System.out.printf("Hex-цифра π на позиции %d = %X (время: %.2fs)%n", pos, hex, (t1 - t0) / 1000.0);
        } else if (base == 10) {
            System.out.println("Запрос: dec-цифра на позиции " + pos);
            System.out.println("A — блок hex→dec (быстрее, приближённо).");
            System.out.println("B — Apfloat (надёжно, вычислит все предыдущие цифры).");
            System.out.print("Выбери A или B: ");
            String choice = sc.nextLine().trim().toUpperCase();
            if ("B".equals(choice)) {
                try {
                    int digitsNeeded = (pos > Integer.MAX_VALUE - 10) ? Integer.MAX_VALUE - 10 : (int) pos + 5;
                    Apfloat pi = ApfloatMath.pi(digitsNeeded);
                    String s = pi.toString(true);
                    int idx = s.indexOf('.') + 1 + (int) (pos - 1);
                    if (idx < s.length()) {
                        char digit = s.charAt(idx);
                        System.out.println("Десятичная цифра №" + pos + " = " + digit);
                    } else {
                        System.out.println("Apfloat вернул меньше цифр, чем ожидалось.");
                    }
                } catch (NoClassDefFoundError e) {
                    System.out.println("Apfloat не доступен. Подключи apfloat.jar и повтори.");
                } catch (OutOfMemoryError oom) {
                    System.err.println("Недостаточно памяти для Apfloat с таким количеством цифр.");
                } catch (Exception e) {
                    System.err.println("Ошибка Apfloat: " + e.getMessage());
                    e.printStackTrace();
                }
            } else if ("A".equals(choice)) {
                try {
                    double ratio = Math.log(10.0) / Math.log(16.0);
                    long startHex = Math.max(1, (long) Math.floor((pos - 1) * ratio) - 6);
                    int hexBlockLen = 50;
                    StringBuilder hexBlock = new StringBuilder(hexBlockLen);
                    System.out.println("Вычисляем hex-блок от позиции " + startHex + " длиной " + hexBlockLen);
                    for (long h = startHex; h < startHex + hexBlockLen; h++) {
                        int d = bbpHexDigit(h);
                        hexBlock.append(Integer.toHexString(d).toUpperCase());
                    }
                    BigInteger H = new BigInteger(hexBlock.toString(), 16);
                    int L = hexBlockLen;
                    BigDecimal frac = new BigDecimal(H);
                    BigDecimal denom = new BigDecimal(BigInteger.valueOf(16).pow(L + (int) (startHex - 1)));
                    BigDecimal piFragment = frac.divide(denom, new MathContext(80, RoundingMode.HALF_UP));
                    BigDecimal scaled = piFragment.multiply(new BigDecimal(BigInteger.TEN.pow((int) pos)));
                    BigInteger intPart = scaled.toBigInteger();
                    int decimalDigit = intPart.mod(BigInteger.TEN).intValue();
                    System.out.println("Приближённая десятичная цифра №" + pos + " = " + decimalDigit
                            + " (приближённо — возможны переносы; для 100% точности используй Apfloat)");
                } catch (Exception ex) {
                    System.out.println("Ошибка при попытке конвертации hex→dec: " + ex.getMessage());
                    ex.printStackTrace();
                }
            } else {
                System.out.println("Вводить нужно A или B.");
            }
        } else {
            System.out.println("База должна быть 16 или 10.");
        }
    }

    // BBP: hex digit
    private static int bbpHexDigit(long n) {
        long nn = n - 1;
        double x = 0.0;
        x += 4.0 * series(1, nn);
        x -= 2.0 * series(4, nn);
        x -= 1.0 * series(5, nn);
        x -= 1.0 * series(6, nn);
        x = x - Math.floor(x);
        int digit = (int) (16.0 * x);
        if (digit < 0) digit = 0;
        if (digit > 15) digit = 15;
        return digit;
    }

    private static double series(int j, long n) {
        double s = 0.0;
        for (long k = 0; k <= n; k++) {
            long denom = 8L * k + j;
            long exp = n - k;
            long t = modPow16(exp, denom);
            s += (double) t / (double) denom;
            s = s - Math.floor(s);
        }
        long k = n + 1;
        while (true) {
            double denom = 8.0 * k + j;
            double tTerm = Math.pow(16.0, (double) (n - k)) / denom;
            if (tTerm < 1e-17) break;
            s += tTerm;
            s = s - Math.floor(s);
            k++;
            if (k > n + 1000) break;
        }
        return s - Math.floor(s);
    }

    private static long modPow16(long exp, long m) {
        if (m <= 1) return 0;
        if (m <= Integer.MAX_VALUE) {
            return modPow16Long(exp, (int) m);
        } else {
            BigInteger res = BigInteger.valueOf(16).modPow(BigInteger.valueOf(exp), BigInteger.valueOf(m));
            return res.longValue();
        }
    }

    private static long modPow16Long(long exp, int mod) {
        if (mod == 1) return 0;
        long result = 1 % mod;
        long base = 16 % mod;
        long e = exp;
        while (e > 0) {
            if ((e & 1L) == 1L) result = (result * base) % mod;
            base = (base * base) % mod;
            e >>= 1;
        }
        return result;
    }
}

// как искпользовать?
// 1. Запустить программу.
// 2. Выбрать один из доступных методов вычисления π.
// 3. Программа начнет вычислять значение π и выводить его на экран.
// 4. Для выхода из программы нажмите 0 при запросе выбора метода. Или просто закройте консоль.

// Какие методы есть?
// 1. Формула Мэчина
// 2. Ряд Нилаканты
// 3. Спиготовый алгоритм (Rabinowitz–Wagon)
// 4. Библиотека Apfloat

// Что такое Apfloat?
// Apfloat — это библиотека для работы с большими числами с высокой точностью. Она позволяет выполнять операции над числами, которые могут быть гораздо больше, чем те, что можно хранить в стандартных типах данных Java.

// Почему я должен использовать Apfloat?
// Если вам нужно получить более точное значение π, то использование Apfloat может помочь. Он обеспечивает более высокую точность при вычислениях, чем другие методы.

// PI_num - это программа для вычисления значения числа π с использованием различных методов.