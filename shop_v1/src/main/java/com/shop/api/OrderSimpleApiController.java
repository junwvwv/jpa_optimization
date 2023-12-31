package com.shop.api;

import com.shop.domain.Address;
import com.shop.domain.Order;
import com.shop.domain.OrderSearch;
import com.shop.domain.OrderStatus;
import com.shop.dto.SimpleOrderQueryDto;
import com.shop.repository.OrderRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;

    /**
     * V1 엔티티 직접 노출
     * - 직접 노출 시 양방향 연관관계가 걸린 곳은 한곳을 @JsonIgnore 처리 해야 한다
     * - 지연 로딩이 걸려있을 경우 실제 엔티티 대신 프록시가 존재하는데
     * - jackson 라이브러리가 이 프록시 객체를 json 으로 어떻게 생생해야 하는지 알지 못한다 > 예외 발생
     * - Hibernate5JakartaModule 를 스프링 빈으로 등록하면 해결 가능
     * - 그냥 엔티티를 직접 노출하지 말자 > DTO 로 변환하자
     */
    @GetMapping("/api/v1/simple-orders")
    public List<Order> ordersV1() {
        List<Order> orders = orderRepository.findOrders(new OrderSearch());
        for (Order order : orders) {
            //LAZY 강제 초기화
            order.getMember().getName();
            order.getDelivery().getAddress();
        }
        return orders;
    }

    /**
     * V2 DTO 변환
     * - 엔티티를 DTO 로 변환하는게 가장 일반적인 방법이다
     * - 하지만 지연 로딩으로 쿼리가 총 1 + N + N번 실행된다
     * - N + 1 > 1 + 회원 N + 배송 N
     * - 지연 로딩은 영속성 컨텍스트에서 조회하기 때문에 이미 조회된 경우 쿼리를 생략한다
     * - 만약 주문 조회의 결과가 4건이라면 최악의 경우 1 + 4 + 4번의 쿼리가 발생
     */
    @GetMapping("/api/v2/simple-orders")
    public List<SimpleOrderDto> ordersV2() {
        return orderRepository.findOrders(new OrderSearch())
                .stream()
                .map(SimpleOrderDto::new)
                .collect(Collectors.toList());
    }

    /**
     * V3 DTO 변환 + 페치 조인
     * - 엔티티를 페치 조인을 사용하여 쿼리 한번에 조회
     * - 페치 조인으로 member 와 delivery 가 이미 조회된 상태로 지연 로딩 X
     */
    @GetMapping("/api/v3/simple-orders")
    public List<SimpleOrderDto> ordersV3() {
        return orderRepository.findOrdersFetch()
                .stream()
                .map(SimpleOrderDto::new)
                .collect(Collectors.toList());
    }

    /**
     * V4 DTO 로 바로 조회
     * - SELECT 절에서 원하는 데이터만 직접 선택하므로 성능 향샹(생각보다 미비)
     * - 하지만 리포지토리 재사용성이 떨어지고 API 스펙에 맞춘 코드가 리포지토리에 들어간다
     */
    @GetMapping("/api/v4/simple-orders")
    public List<SimpleOrderQueryDto> ordersV4() {
        return orderRepository.findOrdersToDto();
    }

    /**
     * 쿼리 선택 순서
     * 1. 우선 엔티티를 DTO 로 변환하는 방법 선택
     * 2. 필요하면 페치 조인으로 성능을 최적화 > 대부분의 이슈 해결
     * 3. 그래도 안되면 DTO 로 직접조회
     * 4. 최후의 방법은 JPA 가 제공하는 네이티브 쿼리 또는 스프링 JDBC Template 으로 직접 쿼리를 날린다
     */

    @Data
    @AllArgsConstructor
    static class SimpleOrderDto {

        private Long orderId;

        private String name;

        private LocalDateTime orderDate;

        private OrderStatus orderStatus;

        private Address address;

        public SimpleOrderDto(Order order) {
            this.orderId = order.getId();
            this.name = order.getMember().getName();
            this.orderDate = order.getOrderDate();
            this.orderStatus = order.getStatus();
            this.address = order.getDelivery().getAddress();
        }
    }

}
