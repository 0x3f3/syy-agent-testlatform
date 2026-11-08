package com.syy.testplatform.demo;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** Local-only fixture API so the imported sample OpenAPI can be executed end-to-end. */
@Tag(name = "Demo Shop 示例接口")
@RestController
@ConditionalOnProperty(prefix="app.demo-api",name="enabled",havingValue="true",matchIfMissing=false)
@RequestMapping("/v1")
public class DemoShopController {
 private final Map<Long,Map<String,Object>> users=new ConcurrentHashMap<>();
 private final Map<Long,Map<String,Object>> products=new ConcurrentHashMap<>();
 private final Map<Long,Map<String,Object>> orders=new ConcurrentHashMap<>();
 private final AtomicLong userIds=new AtomicLong(1002),orderIds=new AtomicLong(3001);
 @Value("${demo.shop.username}") private String demoUsername;
 @Value("${demo.shop.password}") private String demoPassword;
 @Value("${demo.shop.token}") private String demoToken;
 public DemoShopController(){
  users.put(1001L,new LinkedHashMap<>(Map.of("id",1001L,"username","tester","email","tester@example.com","status","ACTIVE","age",25)));
  users.put(1002L,new LinkedHashMap<>(Map.of("id",1002L,"username","qa_user","email","qa@example.com","status","DISABLED","age",27)));
  products.put(2001L,new LinkedHashMap<>(Map.of("id",2001L,"name","机械键盘","category","电脑配件","price",299.0,"stock",50,"status","ON_SALE")));
  products.put(2002L,new LinkedHashMap<>(Map.of("id",2002L,"name","无线鼠标","category","电脑配件","price",99.0,"stock",0,"status","OFF_SALE")));
  orders.put(3001L,new LinkedHashMap<>(Map.of("id",3001L,"userId",1001L,"status","CREATED","totalAmount",299.0)));
 }
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","UP","service","syy-demo-shop");}
 @PostMapping("/auth/login") public ResponseEntity<?> login(@RequestBody Map<String,Object> body){
  if(demoUsername.equals(String.valueOf(body.get("username")))&&demoPassword.equals(String.valueOf(body.get("password"))))return ResponseEntity.ok(Map.of("accessToken",demoToken,"tokenType","Bearer","expiresIn",3600));
  return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","用户名或密码错误"));
 }
 @GetMapping("/users") public ResponseEntity<?> users(@RequestHeader(value="Authorization",required=false)String auth,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int pageSize,@RequestParam(required=false)String keyword,@RequestParam(required=false)String status){
  if(!authorized(auth))return unauthorized();if(page<1||pageSize<1||pageSize>100)return ResponseEntity.badRequest().body(Map.of("message","分页参数错误"));
  List<Map<String,Object>> rows=users.values().stream().filter(u->keyword==null||String.valueOf(u.get("username")).contains(keyword)||String.valueOf(u.get("email")).contains(keyword)).filter(u->status==null||status.equals(u.get("status"))).sorted(Comparator.comparing(u->((Number)u.get("id")).longValue())).collect(Collectors.toList());
  int from=Math.min(rows.size(),(page-1)*pageSize),to=Math.min(rows.size(),from+pageSize);return ResponseEntity.ok(Map.of("page",page,"pageSize",pageSize,"total",rows.size(),"items",rows.subList(from,to)));
 }
 @PostMapping("/users") public ResponseEntity<?> createUser(@RequestHeader(value="Authorization",required=false)String auth,@RequestBody Map<String,Object> body){
  if(!authorized(auth))return unauthorized();String username=String.valueOf(body.getOrDefault("username",""));String email=String.valueOf(body.getOrDefault("email",""));String password=String.valueOf(body.getOrDefault("password",""));
  if(username.length()<3||email.isBlank()||!email.contains("@")||password.length()<8)return ResponseEntity.badRequest().body(Map.of("message","参数校验失败"));
  if(users.values().stream().anyMatch(u->username.equals(u.get("username"))||email.equals(u.get("email"))))return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message","用户名或邮箱已存在"));
  long id=userIds.incrementAndGet();Map<String,Object> user=new LinkedHashMap<>();user.put("id",id);user.put("username",username);user.put("email",email);user.put("status","ACTIVE");user.put("age",body.getOrDefault("age",18));users.put(id,user);return ResponseEntity.status(HttpStatus.CREATED).body(user);
 }
 @GetMapping("/users/{id}") public ResponseEntity<?> getUser(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable long id){if(!authorized(auth))return unauthorized();Map<String,Object> user=users.get(id);return user==null?ResponseEntity.notFound().build():ResponseEntity.ok(user);}
 @PutMapping("/users/{id}") public ResponseEntity<?> updateUser(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable long id,@RequestBody Map<String,Object> body){if(!authorized(auth))return unauthorized();Map<String,Object> user=users.get(id);if(user==null)return ResponseEntity.notFound().build();if(body.containsKey("email"))user.put("email",body.get("email"));if(body.containsKey("age"))user.put("age",body.get("age"));return ResponseEntity.ok(user);}
 @DeleteMapping("/users/{id}") public ResponseEntity<?> deleteUser(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable long id){if(!authorized(auth))return unauthorized();return users.remove(id)==null?ResponseEntity.notFound().build():ResponseEntity.noContent().build();}
 @GetMapping("/products") public ResponseEntity<?> products(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int pageSize,@RequestParam(required=false)String category,@RequestParam(required=false)Double minPrice,@RequestParam(required=false)Double maxPrice){
  if(page<1||pageSize<1||pageSize>100||(minPrice!=null&&minPrice<0)||(maxPrice!=null&&maxPrice<0)||(minPrice!=null&&maxPrice!=null&&minPrice>maxPrice))return ResponseEntity.badRequest().body(Map.of("message","查询参数错误"));
  List<Map<String,Object>> rows=products.values().stream().filter(p->category==null||category.equals(p.get("category"))).filter(p->minPrice==null||((Number)p.get("price")).doubleValue()>=minPrice).filter(p->maxPrice==null||((Number)p.get("price")).doubleValue()<=maxPrice).collect(Collectors.toList());int from=Math.min(rows.size(),(page-1)*pageSize),to=Math.min(rows.size(),from+pageSize);return ResponseEntity.ok(Map.of("page",page,"pageSize",pageSize,"total",rows.size(),"items",rows.subList(from,to)));
 }
 @GetMapping("/products/{id}") public ResponseEntity<?> getProduct(@PathVariable long id){Map<String,Object> product=products.get(id);return product==null?ResponseEntity.notFound().build():ResponseEntity.ok(product);}
 @GetMapping("/orders") public ResponseEntity<?> orders(@RequestHeader(value="Authorization",required=false)String auth,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int pageSize,@RequestParam(required=false)String status){if(!authorized(auth))return unauthorized();if(page<1||pageSize<1||pageSize>100)return ResponseEntity.badRequest().body(Map.of("message","分页参数错误"));List<Map<String,Object>> rows=orders.values().stream().filter(o->status==null||status.equals(o.get("status"))).collect(Collectors.toList());int from=Math.min(rows.size(),(page-1)*pageSize),to=Math.min(rows.size(),from+pageSize);return ResponseEntity.ok(Map.of("page",page,"pageSize",pageSize,"total",rows.size(),"items",rows.subList(from,to)));}
 @PostMapping("/orders") public ResponseEntity<?> createOrder(@RequestHeader(value="Authorization",required=false)String auth,@RequestBody Map<String,Object> body){
  if(!authorized(auth))return unauthorized();Object raw=body.get("items");if(!(raw instanceof List<?> items)||items.isEmpty())return ResponseEntity.badRequest().body(Map.of("message","订单商品不能为空"));double total=0;
  for(Object value:items){if(!(value instanceof Map<?,?> item))return ResponseEntity.badRequest().body(Map.of("message","商品参数错误"));long productId=Long.parseLong(String.valueOf(item.get("productId")));int quantity=Integer.parseInt(String.valueOf(item.get("quantity")));Map<String,Object> product=products.get(productId);if(quantity<1||quantity>99||product==null)return ResponseEntity.badRequest().body(Map.of("message","商品或数量错误"));int stock=((Number)product.get("stock")).intValue();if(stock<quantity)return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message","库存不足"));total+=((Number)product.get("price")).doubleValue()*quantity;product.put("stock",stock-quantity);}
  long id=orderIds.incrementAndGet();Map<String,Object> order=new LinkedHashMap<>();order.put("id",id);order.put("userId",1001L);order.put("status","CREATED");order.put("totalAmount",total);orders.put(id,order);return ResponseEntity.status(HttpStatus.CREATED).body(order);
 }
 @GetMapping("/orders/{id}") public ResponseEntity<?> getOrder(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable long id){if(!authorized(auth))return unauthorized();Map<String,Object> order=orders.get(id);if(order==null)return ResponseEntity.notFound().build();if(!Objects.equals(order.get("userId"),1001L))return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","无权查看该订单"));return ResponseEntity.ok(order);}
 @PostMapping("/orders/{id}/cancel") public ResponseEntity<?> cancelOrder(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable long id,@RequestBody(required=false)Map<String,Object> body){if(!authorized(auth))return unauthorized();Map<String,Object> order=orders.get(id);if(order==null)return ResponseEntity.notFound().build();if(!"CREATED".equals(order.get("status")))return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message","当前订单状态不允许取消"));order.put("status","CANCELLED");return ResponseEntity.ok(order);}
 private boolean authorized(String auth){return ("Bearer "+demoToken).equals(auth);}
 private ResponseEntity<Map<String,String>> unauthorized(){return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","未认证或令牌无效"));}
}
