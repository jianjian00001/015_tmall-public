package com.xq.tmall.controller.admin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.xq.tmall.controller.BaseController;
import com.xq.tmall.entity.Address;
import com.xq.tmall.entity.Product;
import com.xq.tmall.entity.ProductOrder;
import com.xq.tmall.entity.ProductOrderItem;
import com.xq.tmall.service.*;
import com.xq.tmall.util.OrderUtil;
import com.xq.tmall.util.PageUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * 后台管理-订单页
 */
@Controller
public class
OrderController extends BaseController{
    @Resource(name="productOrderService")
    private ProductOrderService productOrderService;
    @Resource(name = "addressService")
    private AddressService addressService;
    @Resource(name="userService")
    private UserService userService;
    @Resource(name = "productOrderItemService")
    private ProductOrderItemService productOrderItemService;
    @Resource(name = "productService")
    private ProductService productService;
    @Resource(name = "productImageService")
    private ProductImageService productImageService;
    @Resource(name = "lastIDService")
    private LastIDService lastIDService;

    //转到后台管理-订单页-ajax
    @RequestMapping(value = "admin/order", method = RequestMethod.GET)
    public String goToPage(HttpSession session, Map<String, Object> map){
        logger.info("获取前10条订单列表");
        PageUtil pageUtil = new PageUtil(0, 10);
        List<ProductOrder> productOrderList =
                productOrderService.getList(
                        null,
                        null,
                        new OrderUtil("productOrder_id",
                                true), pageUtil);
        map.put("productOrderList",productOrderList);
        logger.info("获取订单总数量");
        Integer productOrderCount = productOrderService.getTotal(null, null);
        map.put("productOrderCount", productOrderCount);
        logger.info("获取分页信息");
        pageUtil.setTotal(productOrderCount);
        map.put("pageUtil", pageUtil);

        logger.info("转到后台管理-订单页-ajax方式");
        return "admin/orderManagePage";
    }

    //转到后台管理-订单详情页-ajax
    @RequestMapping(value = "admin/order/{oid}", method = RequestMethod.GET)
    public String goToDetailsPage(HttpSession session, Map<String, Object> map, @PathVariable Integer oid/* 订单ID */) {
        logger.info("获取order_id为{}的订单信息",oid);
        ProductOrder order = productOrderService.get(oid);
        logger.info("获取订单详情-地址信息");
        Address address = addressService.get(order.getProductOrder_address().getAddress_areaId());
        Stack<String> addressStack = new Stack<>();
        //详细地址
        addressStack.push(order.getProductOrder_detail_address());
        //最后一级地址
        addressStack.push(address.getAddress_name() + " ");
        //如果不是第一级地址，循环拼接地址信息
        while (!address.getAddress_areaId().equals(address.getAddress_regionId().getAddress_areaId())) {
            address = addressService.get(address.getAddress_regionId().getAddress_areaId());
            addressStack.push(address.getAddress_name() + " ");
        }
        StringBuilder builder = new StringBuilder();
        while (!addressStack.empty()) {
            builder.append(addressStack.pop());
        }
        logger.info("订单地址字符串：{}", builder);
        order.setProductOrder_detail_address(builder.toString());
        logger.info("获取订单详情-用户信息");
        order.setProductOrder_user(userService.get(order.getProductOrder_user().getUser_id()));
        logger.info("获取订单详情-订单项信息");
        List<ProductOrderItem> productOrderItemList = productOrderItemService.getListByOrderId(oid, null);
        if (productOrderItemList != null) {
            logger.info("获取订单详情-订单项对应的产品信息");
            for (ProductOrderItem productOrderItem : productOrderItemList) {
                Integer productId = productOrderItem.getProductOrderItem_product().getProduct_id();
                logger.info("获取产品ID为{}的产品信息", productId);
                Product product = productService.get(productId);
                if (product != null) {
                    logger.info("获取产品ID为{}的第一张预览图片信息", productId);
                    product.setSingleProductImageList(productImageService.getList(productId, (byte) 0, new PageUtil(0, 1)));
                }
                productOrderItem.setProductOrderItem_product(product);
            }
        }
        order.setProductOrderItemList(productOrderItemList);
        map.put("order", order);
        logger.info("转到后台管理-订单详情页-ajax方式");
        return "admin/include/orderDetails";
    }

    //更新订单信息-ajax
    @ResponseBody
    @RequestMapping(value = "admin/order/{order_id}", method = RequestMethod.PUT, produces = "application/json;charset=UTF-8")
    public String updateOrder(@PathVariable("order_id") String order_id) {
        JSONObject jsonObject = new JSONObject();
        logger.info("整合订单信息");
        ProductOrder productOrder = new ProductOrder()
                .setProductOrder_id(Integer.valueOf(order_id))
                .setProductOrder_status((byte) 2)
                .setProductOrder_delivery_date(new Date());
        logger.info("更新订单信息，订单ID值为：{}", order_id);
        boolean yn = productOrderService.update(productOrder);
        if (yn) {
            logger.info("更新成功！");
            jsonObject.put("success", true);
        } else {
            logger.info("更新失败！事务回滚");
            jsonObject.put("success", false);
            throw new RuntimeException();
        }
        jsonObject.put("order_id", order_id);
        return jsonObject.toJSONString();
    }

    //按条件查询订单-ajax
    @ResponseBody
    @RequestMapping(value = "admin/order/{index}/{count}", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public String getOrderBySearch(@RequestParam(required = false) String productOrder_code/* 订单号 */,
                                   @RequestParam(required = false) String productOrder_post/* 订单邮政编码 */,
                                   @RequestParam(required = false) Byte[] productOrder_status_array/* 订单状态数组 */,
                                   @RequestParam(required = false) String orderBy/* 排序字段 */,
                                   @RequestParam(required = false,defaultValue = "true") Boolean isDesc/* 是否倒序 */,
                                   @PathVariable Integer index/* 页数 */,
                                   @PathVariable Integer count/* 行数 */){
        //移除不必要条件
        if (productOrder_status_array != null && (productOrder_status_array.length <= 0 || productOrder_status_array.length >=5)) {
            productOrder_status_array = null;
        }
        if (productOrder_code != null){
            productOrder_code = "".equals(productOrder_code) ? null : productOrder_code;
        }
        if(productOrder_post != null){
            productOrder_post = "".equals(productOrder_post) ? null : productOrder_post;
        }
        if (orderBy != null && "".equals(orderBy)) {
            orderBy = null;
        }
        //封装查询条件
        ProductOrder productOrder = new ProductOrder()
                .setProductOrder_code(productOrder_code)
                .setProductOrder_post(productOrder_post);
        OrderUtil orderUtil = null;
        if (orderBy != null) {
            logger.info("根据{}排序，是否倒序:{}",orderBy,isDesc);
            orderUtil = new OrderUtil(orderBy, isDesc);
        } else {
            orderUtil = new OrderUtil("productOrder_id",
                    true);
        }
        JSONObject object = new JSONObject();
        logger.info("按条件获取第{}页的{}条订单", index + 1, count);
        PageUtil pageUtil = new PageUtil(index, count);
        List<ProductOrder> productOrderList = productOrderService.getList(productOrder, productOrder_status_array, orderUtil, pageUtil);
        object.put("productOrderList", JSONArray.parseArray(JSON.toJSONString(productOrderList)));
        logger.info("按条件获取订单总数量");
        Integer productOrderCount = productOrderService.getTotal(productOrder, productOrder_status_array);
        object.put("productOrderCount", productOrderCount);
        logger.info("获取分页信息");
        pageUtil.setTotal(productOrderCount);
        object.put("totalPage", pageUtil.getTotalPage());
        object.put("pageUtil", pageUtil);

        return object.toJSONString();
    }
}
