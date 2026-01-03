package com.example.revenueservice.service;

import com.example.revenueservice.client.HotelCapacityClient;
import com.example.revenueservice.client.HotelGrpcClient;
import com.example.revenueservice.dto.AdminRevenueResponse;
import com.example.revenueservice.dto.AdminSummaryResponse;
import com.example.revenueservice.dto.BookingStatusBreakdown;
import com.example.revenueservice.dto.CustomerAnalytics;
import com.example.revenueservice.dto.GrowthMetrics;
import com.example.revenueservice.dto.GroupBy;
import com.example.revenueservice.dto.HotelRevenueSeries;
import com.example.revenueservice.dto.HotelSummaryItem;
import com.example.revenueservice.dto.OccupancyResponse;
import com.example.revenueservice.dto.OwnerRevenueResponse;
import com.example.revenueservice.dto.OwnerSummaryResponse;
import com.example.revenueservice.dto.RegionalRevenue;
import com.example.revenueservice.dto.RevenuePoint;
import com.example.revenueservice.dto.RevenueResponse;
import com.example.revenueservice.dto.RoomTypeRevenue;
import com.example.revenueservice.dto.SummaryResponse;
import com.example.revenueservice.dto.TopHotelItem;
import com.example.revenueservice.entity.BookingStatusRecord;
import com.example.revenueservice.entity.PaymentRecord;
import com.example.revenueservice.grpc.GetHotelByIdResponse;
import com.example.revenueservice.grpc.RoomTypeBasic;
import com.example.revenueservice.repository.BookingStatusRecordRepository;
import com.example.revenueservice.repository.PaymentRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class RevenueReportService {

    private final PaymentRecordRepository paymentRecordRepository;
    private final BookingStatusRecordRepository bookingStatusRecordRepository;
    private final HotelCapacityClient hotelCapacityClient;
    private final HotelGrpcClient hotelGrpcClient;
    private static final Set<String> VALID_OCCUPANCY_STATUS =
            Set.of(
                    "CONFIRMED",
                    "CHECKED_IN",
                    "CHECKED_OUT"
            );

    public RevenueReportService(PaymentRecordRepository paymentRecordRepository,
                                BookingStatusRecordRepository bookingStatusRecordRepository,
                                HotelCapacityClient hotelCapacityClient,
                                HotelGrpcClient hotelGrpcClient) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.bookingStatusRecordRepository = bookingStatusRecordRepository;
        this.hotelCapacityClient = hotelCapacityClient;
        this.hotelGrpcClient = hotelGrpcClient;
    }

    @Transactional(readOnly = true)
    public RevenueResponse revenue(String hotelId, LocalDate from, LocalDate to, GroupBy groupBy) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<PaymentRecord> payments = hotelId == null
                ? paymentRecordRepository.findByEventAtBetween(start, end)
                : paymentRecordRepository.findByHotelIdAndEventAtBetween(hotelId, start, end);

        Map<LocalDate, BigDecimal> buckets = new TreeMap<>();
        for (PaymentRecord record : payments) {
            BigDecimal signedAmount = record.getPaymentStatus() != null && record.getPaymentStatus().equalsIgnoreCase("REFUNDED")
                    ? record.getAmount().negate()
                    : record.getAmount();
            LocalDate keyDate = resolvePeriod(record, groupBy);
            buckets.merge(keyDate, signedAmount, BigDecimal::add);
        }

        List<RevenuePoint> points = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : buckets.entrySet()) {
            points.add(new RevenuePoint(entry.getKey(), entry.getValue()));
        }
        return new RevenueResponse(points);
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(String hotelId, LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<PaymentRecord> payments = hotelId == null
            ? paymentRecordRepository.findByEventAtBetween(start, end)
            : paymentRecordRepository.findByHotelIdAndEventAtBetween(hotelId, start, end);
        BigDecimal totalRevenue = BigDecimal.ZERO;
        int completedCount = 0;
        for (PaymentRecord record : payments) {
            BigDecimal signed = record.getPaymentStatus() != null && record.getPaymentStatus().equalsIgnoreCase("REFUNDED")
                    ? record.getAmount().negate()
                    : record.getAmount();
            totalRevenue = totalRevenue.add(signed);
            if (record.getPaymentStatus() != null && record.getPaymentStatus().equalsIgnoreCase("COMPLETED")) {
                completedCount++;
            }
        }

        BigDecimal averageRevenue = completedCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(completedCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        double cancellationRate = calculateCancellationRate(hotelId, start, end);
        OccupancyResponse occupancy = occupancy(hotelId, from, to);

        return new SummaryResponse(totalRevenue, averageRevenue, occupancy.occupancyRate(), cancellationRate);
    }

//    @Transactional(readOnly = true)
//    public OccupancyResponse occupancy(String hotelId, LocalDate from, LocalDate to) {
//        LocalDateTime start = from.atStartOfDay();
//        LocalDateTime end = to.plusDays(1).atStartOfDay();
//        List<PaymentRecord> payments = hotelId == null
//                ? paymentRecordRepository.findByEventAtBetween(start, end)
//                : paymentRecordRepository.findByHotelIdAndEventAtBetween(hotelId, start, end);
//
//        long roomNights = 0;
//        Set<String> roomTypeIds = new HashSet<>();
//
//        for (PaymentRecord record : payments) {
//            int nights = record.getNights() == null ? 0 : record.getNights();
//            int rooms = record.getRooms() == null ? 0 : record.getRooms();
//            long delta = (long) nights * rooms;
//            if (record.getPaymentStatus() != null && record.getPaymentStatus().equalsIgnoreCase("REFUNDED")) {
//                roomNights -= delta;
//            } else if (record.getPaymentStatus() != null && record.getPaymentStatus().equalsIgnoreCase("COMPLETED")) {
//                roomNights += delta;
//            }
//            if (record.getRoomTypeId() != null) {
//                roomTypeIds.add(record.getRoomTypeId());
//            }
//        }
//
//        if (roomNights < 0) {
//            roomNights = 0;
//        }
//
//        long totalRooms = 0;
//        for (String roomTypeId : roomTypeIds) {
//            totalRooms += hotelCapacityClient.getTotalRooms(roomTypeId);
//        }
//
//        long days = ChronoUnit.DAYS.between(from, to) + 1;
//        long capacityNights = days > 0 ? totalRooms * days : 0;
//        double occupancyRate = capacityNights > 0 ? Math.min(1.0, (double) roomNights / capacityNights) : 0.0;
//
//        return new OccupancyResponse(occupancyRate, roomNights, capacityNights);
//    }

    @Transactional(readOnly = true)
    public OccupancyResponse occupancy(String hotelId, LocalDate from, LocalDate to) {

        List<BookingStatusRecord> records =
                hotelId == null
                        ? bookingStatusRecordRepository.findActiveBookings(from, to)
                        : bookingStatusRecordRepository.findActiveBookingsByHotel(hotelId, from, to);

        long roomNights = 0;

        for (BookingStatusRecord r : records) {
            if (!VALID_OCCUPANCY_STATUS.contains(r.getBookingStatus())) {
                continue;
            }

            LocalDate stayFrom = r.getCheckInDate().isAfter(from)
                    ? r.getCheckInDate()
                    : from;

            LocalDate stayTo = r.getCheckOutDate().isBefore(to.plusDays(1))
                    ? r.getCheckOutDate()
                    : to.plusDays(1);

            long nights = ChronoUnit.DAYS.between(stayFrom, stayTo);
            if (nights > 0) {
                roomNights += nights * r.getRooms();
            }
        }
        GetHotelByIdResponse info = hotelGrpcClient.getHotelById(hotelId);
        long totalRooms = 0;
        for(RoomTypeBasic roomType : info.getRoomTypesList()){
            totalRooms += roomType.getTotal();
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        long capacityNights = totalRooms * days;
        System.out.println("capacityNights: " + capacityNights);
        System.out.println("roomNights: " + roomNights);

        double occupancyRate =
                capacityNights > 0 ? (double) roomNights / capacityNights : 0;

        return new OccupancyResponse(occupancyRate, roomNights, capacityNights);
    }


    private double calculateCancellationRate(String hotelId, LocalDateTime start, LocalDateTime end) {
        List<BookingStatusRecord> records = hotelId == null
                ? bookingStatusRecordRepository.findByEventAtBetween(start, end)
                : bookingStatusRecordRepository.findByHotelIdAndEventAtBetween(hotelId, start, end);
        Map<String, BookingStatusRecord> latestByBooking = new HashMap<>();
        records.sort(Comparator.comparing(BookingStatusRecord::getEventAt));
        for (BookingStatusRecord record : records) {
            latestByBooking.put(record.getBookingId(), record);
        }

        if (latestByBooking.isEmpty()) {
            return 0.0;
        }

        long cancelled = latestByBooking.values().stream()
                .filter(r -> {
                    String status = r.getBookingStatus();
                    return status != null && (status.equalsIgnoreCase("CANCELLED") || status.equalsIgnoreCase("NO_SHOW"));
                })
                .count();

        return (double) cancelled / latestByBooking.size();
    }

    private LocalDate resolvePeriod(PaymentRecord record, GroupBy groupBy) {
        LocalDate baseDate = record.getPaidAt() != null ? record.getPaidAt().toLocalDate() : record.getEventAt().toLocalDate();
        return switch (groupBy) {
            case MONTH -> baseDate.withDayOfMonth(1);
            case YEAR -> baseDate.withDayOfYear(1);
            default -> baseDate;
        };
    }

    @Transactional(readOnly = true)
    public OwnerRevenueResponse ownerRevenue(String ownerId, LocalDate from, LocalDate to, GroupBy groupBy) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<PaymentRecord> payments = paymentRecordRepository.findByEventAtBetween(start, end);

        Map<String, List<PaymentRecord>> byHotel = new HashMap<>();
        for (PaymentRecord record : payments) {
            HotelGrpcClient.HotelOwnerInfo info = hotelGrpcClient.getHotelInfo(record.getHotelId());
            // include when owner matches (case-insensitive) and owner id is present
            if (info.ownerId() != null && ownerId != null && info.ownerId().equalsIgnoreCase(ownerId)) {
                byHotel.computeIfAbsent(record.getHotelId(), k -> new ArrayList<>()).add(record);
            }
        }

        List<HotelRevenueSeries> series = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, List<PaymentRecord>> entry : byHotel.entrySet()) {
            String hid = entry.getKey();
            HotelGrpcClient.HotelOwnerInfo info = hotelGrpcClient.getHotelInfo(hid);
            RevenueResponse revenue = buildRevenueFromRecords(entry.getValue(), groupBy);
            BigDecimal hotelSum = revenue.data().stream()
                    .map(RevenuePoint::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            total = total.add(hotelSum);
            series.add(new HotelRevenueSeries(hid, info.hotelName(), revenue));
        }
        series.sort(Comparator.comparing(HotelRevenueSeries::hotelName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return new OwnerRevenueResponse(total, series);
    }

    @Transactional(readOnly = true)
    public OwnerSummaryResponse ownerSummary(String ownerId, LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<PaymentRecord> payments = paymentRecordRepository.findByEventAtBetween(start, end);

        Map<String, List<PaymentRecord>> paymentsByHotel = new HashMap<>();
        Map<String, List<BookingStatusRecord>> statusByHotel = new HashMap<>();

        for (PaymentRecord record : payments) {
            HotelGrpcClient.HotelOwnerInfo info = hotelGrpcClient.getHotelInfo(record.getHotelId());
            System.out.println("OwnerId: " + ownerId + ", Hotel OwnerId: " + info.ownerId());
            if (info.ownerId() != null && info.ownerId().equalsIgnoreCase(ownerId)) {
                paymentsByHotel.computeIfAbsent(record.getHotelId(), k -> new ArrayList<>()).add(record);
            }
        }

        List<BookingStatusRecord> statuses = bookingStatusRecordRepository.findByEventAtBetween(start, end);
        for (BookingStatusRecord record : statuses) {
            HotelGrpcClient.HotelOwnerInfo info = hotelGrpcClient.getHotelInfo(record.getHotelId());
            if (info.ownerId() != null && ownerId != null && info.ownerId().equalsIgnoreCase(ownerId)) {
                statusByHotel.computeIfAbsent(record.getHotelId(), k -> new ArrayList<>()).add(record);
            }
        }

        List<HotelSummaryItem> items = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;

        for (Map.Entry<String, List<PaymentRecord>> entry : paymentsByHotel.entrySet()) {
            String hid = entry.getKey();
            HotelGrpcClient.HotelOwnerInfo info = hotelGrpcClient.getHotelInfo(hid);

            BigDecimal hotelRevenue = BigDecimal.ZERO;
            int completedCount = 0;
            for (PaymentRecord record : entry.getValue()) {
                BigDecimal signed = record.getPaymentStatus() != null && record.getPaymentStatus().equalsIgnoreCase("REFUNDED")
                        ? record.getAmount().negate()
                        : record.getAmount();
                hotelRevenue = hotelRevenue.add(signed);
                if (record.getPaymentStatus() != null && record.getPaymentStatus().equalsIgnoreCase("COMPLETED")) {
                    completedCount++;
                }
            }
            totalRevenue = totalRevenue.add(hotelRevenue);
            BigDecimal avg = completedCount > 0
                    ? hotelRevenue.divide(BigDecimal.valueOf(completedCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            List<BookingStatusRecord> hotelStatuses = statusByHotel.getOrDefault(hid, List.of());
            double cancelRate = calculateCancellationRateInternal(hotelStatuses);

            // Calculate platform commission (10%) and net revenue (90%)
            BigDecimal platformCommission = hotelRevenue.multiply(BigDecimal.valueOf(0.10));
            BigDecimal netRevenue = hotelRevenue.multiply(BigDecimal.valueOf(0.90));

            // Get occupancy rate with fallback to 0.0 if error occurs
            double occupancyRate = 0.0;
            try {
                OccupancyResponse occ = occupancy(hid, from, to);
                occupancyRate = occ.occupancyRate();
            } catch (Exception e) {
                System.err.println("Warning: Could not calculate occupancy for hotel " + hid + ": " + e.getMessage());
            }

            items.add(new HotelSummaryItem(
                    hid, 
                    info.hotelName(), 
                    hotelRevenue, 
                    avg, 
                    occupancyRate, 
                    cancelRate,
                    platformCommission,
                    netRevenue,
                    null, // bookingStatusBreakdown
                    null  // roomTypeRevenue
            ));
        }

        items.sort(Comparator.comparing(HotelSummaryItem::hotelName, Comparator.nullsLast(String::compareToIgnoreCase)));
        
        // Calculate totals for net revenue and commission
        BigDecimal totalNetRevenue = totalRevenue != null ? totalRevenue.multiply(BigDecimal.valueOf(0.90)) : BigDecimal.ZERO;
        BigDecimal totalCommission = totalRevenue != null ? totalRevenue.multiply(BigDecimal.valueOf(0.10)) : BigDecimal.ZERO;
        
        return new OwnerSummaryResponse(totalRevenue, totalNetRevenue, totalCommission, items);
    }

    private RevenueResponse buildRevenueFromRecords(List<PaymentRecord> records, GroupBy groupBy) {
        Map<LocalDate, BigDecimal> buckets = new TreeMap<>();
        for (PaymentRecord record : records) {
            BigDecimal signedAmount = record.getPaymentStatus() != null && record.getPaymentStatus().equalsIgnoreCase("REFUNDED")
                    ? record.getAmount().negate()
                    : record.getAmount();
            LocalDate keyDate = resolvePeriod(record, groupBy);
            buckets.merge(keyDate, signedAmount, BigDecimal::add);
        }
        List<RevenuePoint> points = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : buckets.entrySet()) {
            points.add(new RevenuePoint(entry.getKey(), entry.getValue()));
        }
        return new RevenueResponse(points);
    }

    private double calculateCancellationRateInternal(List<BookingStatusRecord> records) {
        Map<String, BookingStatusRecord> latestByBooking = new HashMap<>();
        records.sort(Comparator.comparing(BookingStatusRecord::getEventAt));
        for (BookingStatusRecord record : records) {
            latestByBooking.put(record.getBookingId(), record);
        }
        if (latestByBooking.isEmpty()) {
            return 0.0;
        }
        long cancelled = latestByBooking.values().stream()
                .filter(r -> {
                    String status = r.getBookingStatus();
                    return status != null && (status.equalsIgnoreCase("CANCELLED") || status.equalsIgnoreCase("NO_SHOW"));
                })
                .count();
        return (double) cancelled / latestByBooking.size();
    }

    // ============== ADMIN METHODS ==============

    @Transactional(readOnly = true)
    public AdminSummaryResponse adminSummary(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        
        List<PaymentRecord> allPayments = paymentRecordRepository.findByEventAtBetween(start, end);
        List<BookingStatusRecord> allStatuses = bookingStatusRecordRepository.findByEventAtBetween(start, end);
        
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalBookings = 0;
        BigDecimal totalCommission = BigDecimal.ZERO;
        
        for (PaymentRecord record : allPayments) {
            BigDecimal signed = record.getPaymentStatus() != null && record.getPaymentStatus().equalsIgnoreCase("REFUNDED")
                    ? record.getAmount().negate()
                    : record.getAmount();
            totalRevenue = totalRevenue.add(signed);
            
            BigDecimal commission = signed.multiply(BigDecimal.valueOf(0.10));
            totalCommission = totalCommission.add(commission);
            
            if (record.getPaymentStatus() != null && record.getPaymentStatus().equalsIgnoreCase("COMPLETED")) {
                totalBookings++;
            }
        }
        
        double systemCancellationRate = calculateCancellationRateInternal(allStatuses);
        List<TopHotelItem> topHotels = getTopHotels(from, to, 10);
        List<RegionalRevenue> regionalBreakdown = getRegionalBreakdown(from, to);
        GrowthMetrics monthlyGrowth = calculateMonthlyGrowth(from);
        GrowthMetrics yearlyGrowth = calculateYearlyGrowth(from);
        CustomerAnalytics customerAnalytics = calculateCustomerAnalytics(allPayments);
        
        return new AdminSummaryResponse(
                totalRevenue,
                totalCommission,
                totalBookings,
                systemCancellationRate,
                topHotels,
                regionalBreakdown,
                monthlyGrowth,
                yearlyGrowth,
                customerAnalytics
        );
    }

    @Transactional(readOnly = true)
    public AdminRevenueResponse adminRevenue(LocalDate from, LocalDate to, GroupBy groupBy) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<PaymentRecord> payments = paymentRecordRepository.findByEventAtBetween(start, end);
        
        RevenueResponse systemRevenue = buildRevenueFromRecords(payments, groupBy);
        
        Map<String, List<PaymentRecord>> byHotel = new HashMap<>();
        for (PaymentRecord record : payments) {
            byHotel.computeIfAbsent(record.getHotelId(), k -> new ArrayList<>()).add(record);
        }
        
        List<HotelRevenueSeries> hotelBreakdown = new ArrayList<>();
        for (Map.Entry<String, List<PaymentRecord>> entry : byHotel.entrySet()) {
            String hid = entry.getKey();
            HotelGrpcClient.HotelOwnerInfo info = hotelGrpcClient.getHotelInfo(hid);
            RevenueResponse hotelRevenue = buildRevenueFromRecords(entry.getValue(), groupBy);
            hotelBreakdown.add(new HotelRevenueSeries(hid, info.hotelName(), hotelRevenue));
        }
        
        hotelBreakdown.sort(Comparator.comparing(HotelRevenueSeries::hotelName, Comparator.nullsLast(String::compareToIgnoreCase)));
        
        return new AdminRevenueResponse(systemRevenue.data().stream()
                .map(RevenuePoint::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add), systemRevenue.data(), hotelBreakdown);
    }

    @Transactional(readOnly = true)
    public List<TopHotelItem> getTopHotels(LocalDate from, LocalDate to, int limit) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<PaymentRecord> allPayments = paymentRecordRepository.findByEventAtBetween(start, end);
        
        Map<String, List<PaymentRecord>> byHotel = new HashMap<>();
        Map<String, Long> bookingCountByHotel = new HashMap<>();
        
        for (PaymentRecord record : allPayments) {
            byHotel.computeIfAbsent(record.getHotelId(), k -> new ArrayList<>()).add(record);
            if (record.getPaymentStatus() != null && record.getPaymentStatus().equalsIgnoreCase("COMPLETED")) {
                bookingCountByHotel.merge(record.getHotelId(), 1L, Long::sum);
            }
        }
        
        List<TopHotelItem> items = new ArrayList<>();
        
        for (Map.Entry<String, List<PaymentRecord>> entry : byHotel.entrySet()) {
            String hotelId = entry.getKey();
            HotelGrpcClient.HotelOwnerInfo info = hotelGrpcClient.getHotelInfo(hotelId);

            
            BigDecimal revenue = entry.getValue().stream()
                    .map(p -> p.getPaymentStatus() != null && p.getPaymentStatus().equalsIgnoreCase("REFUNDED")
                            ? p.getAmount().negate()
                            : p.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String city = info.city() != null ? info.city() : "Unknown";
            long bookingCount = bookingCountByHotel.getOrDefault(hotelId, 0L);
            double occupancyRate = occupancy(hotelId, from, to).occupancyRate();
            
            items.add(new TopHotelItem(
                    hotelId,
                    info.hotelName(),
                    city,
                    revenue,
                    bookingCount,
                    occupancyRate
            ));
        }
        
        return items.stream()
                .sorted(Comparator.comparing(TopHotelItem::totalRevenue).reversed())
                .limit(limit)
                .toList();
    }

    private List<RegionalRevenue> getRegionalBreakdown(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<PaymentRecord> allPayments = paymentRecordRepository.findByEventAtBetween(start, end);
        
        Map<String, List<PaymentRecord>> byCity = new HashMap<>();
        Map<String, Set<String>> hotelsByCity = new HashMap<>();
        
        for (PaymentRecord record : allPayments) {
            HotelGrpcClient.HotelOwnerInfo info = hotelGrpcClient.getHotelInfo(record.getHotelId());
            String city = info.city() != null ? info.city() : "Unknown";
            
            byCity.computeIfAbsent(city, k -> new ArrayList<>()).add(record);
            hotelsByCity.computeIfAbsent(city, k -> new HashSet<>()).add(record.getHotelId());
        }
        
        List<RegionalRevenue> regional = new ArrayList<>();
        for (Map.Entry<String, List<PaymentRecord>> entry : byCity.entrySet()) {
            BigDecimal revenue = entry.getValue().stream()
                    .map(p -> p.getPaymentStatus() != null && p.getPaymentStatus().equalsIgnoreCase("REFUNDED")
                            ? p.getAmount().negate()
                            : p.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            long hotelCount = hotelsByCity.getOrDefault(entry.getKey(), new HashSet<>()).size();
            BigDecimal avgRevenue = hotelCount > 0 ? revenue.divide(BigDecimal.valueOf(hotelCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            
            regional.add(new RegionalRevenue(entry.getKey(), revenue, hotelCount, avgRevenue));
        }
        
        return regional.stream()
                .sorted(Comparator.comparing(RegionalRevenue::totalRevenue).reversed())
                .toList();
    }

    private GrowthMetrics calculateMonthlyGrowth(LocalDate currentDate) {
        LocalDate prevMonthStart = currentDate.minusMonths(1).withDayOfMonth(1);
        LocalDate prevMonthEnd = currentDate.minusMonths(1).withDayOfMonth(currentDate.minusMonths(1).lengthOfMonth());
        LocalDate currMonthStart = currentDate.withDayOfMonth(1);
        LocalDate currMonthEnd = currentDate.withDayOfMonth(currentDate.lengthOfMonth());
        
        BigDecimal prevRevenue = calculateTotalRevenue(prevMonthStart, prevMonthEnd);
        BigDecimal currRevenue = calculateTotalRevenue(currMonthStart, currMonthEnd);
        
        BigDecimal growth = currRevenue.subtract(prevRevenue);
        double growthRate = prevRevenue.compareTo(BigDecimal.ZERO) > 0
                ? growth.divide(prevRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;
        
        return new GrowthMetrics(currRevenue, prevRevenue, growthRate, growth);
    }

    private GrowthMetrics calculateYearlyGrowth(LocalDate currentDate) {
        LocalDate prevYearStart = currentDate.minusYears(1).withMonth(1).withDayOfMonth(1);
        LocalDate prevYearEnd = currentDate.minusYears(1).withMonth(12).withDayOfMonth(31);
        LocalDate currYearStart = currentDate.withMonth(1).withDayOfMonth(1);
        LocalDate currYearEnd = currentDate.withMonth(12).withDayOfMonth(31);
        
        BigDecimal prevRevenue = calculateTotalRevenue(prevYearStart, prevYearEnd);
        BigDecimal currRevenue = calculateTotalRevenue(currYearStart, currYearEnd);
        
        BigDecimal growth = currRevenue.subtract(prevRevenue);
        double growthRate = prevRevenue.compareTo(BigDecimal.ZERO) > 0
                ? growth.divide(prevRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;
        
        return new GrowthMetrics(currRevenue, prevRevenue, growthRate, growth);
    }

    private BigDecimal calculateTotalRevenue(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<PaymentRecord> payments = paymentRecordRepository.findByEventAtBetween(start, end);
        
        return payments.stream()
                .map(p -> p.getPaymentStatus() != null && p.getPaymentStatus().equalsIgnoreCase("REFUNDED")
                        ? p.getAmount().negate()
                        : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CustomerAnalytics calculateCustomerAnalytics(List<PaymentRecord> payments) {
        Set<String> uniqueCustomers = new HashSet<>();
        Set<String> uniqueHotelOwners = new HashSet<>();
        
        for (PaymentRecord p : payments) {
            if (p.getPaymentStatus() != null && p.getPaymentStatus().equalsIgnoreCase("COMPLETED")) {
                uniqueCustomers.add(p.getBookingId());
                
                // Get hotel owner info
                HotelGrpcClient.HotelOwnerInfo info = hotelGrpcClient.getHotelInfo(p.getHotelId());
                if (info.ownerId() != null) {
                    uniqueHotelOwners.add(info.ownerId());
                }
            }
        }
        
        long totalCustomers = uniqueCustomers.size();
        long totalHotelOwners = uniqueHotelOwners.size();
        
        return new CustomerAnalytics(totalCustomers, totalHotelOwners);
    }
}
