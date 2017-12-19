/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package websocketx.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSONObject;
import com.zb.kits.MapSort;

public class WebSocketClient {
	private String url;
	private EventLoopGroup group;
	private Channel channel;
	
	public final String accessKey = "";
	public final String secretKey = "";
	public static String serverUrl = "wss://api.zb.com:9999/websocket";
	public static String payPass = "xxxxxxxxx";
	
	public WebSocketClient(String url){
		this.url=url;
	}

	public void connect() throws Exception {
		if (url == null || "".equals(url.trim())) {
			throw new NullPointerException("the url can not be empty");
		}
		URI uri = new URI(url);
		String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
		final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
		final int port;
		if (uri.getPort() == -1) {
			if ("http".equalsIgnoreCase(scheme)) {
				port = 80;
			} else if ("wss".equalsIgnoreCase(scheme)) {
				port = 443;
			} else {
				port = -1;
			}
		} else {
			port = uri.getPort();
		}

		if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
			System.err.println("Only WS(S) is supported.");
			throw new UnsupportedAddressTypeException();
		}
		final boolean ssl = "wss".equalsIgnoreCase(scheme);
		final SslContext sslCtx;
		if (ssl) {
			sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
		} else {
			sslCtx = null;
		}
		group = new NioEventLoopGroup();
		try {
			final WebSocketClientHandler handler = new WebSocketClientHandler(
					WebSocketClientHandshakerFactory.newHandshaker(uri,
							WebSocketVersion.V13, null, false,
							new DefaultHttpHeaders())) {
				@Override
				public void onReceive(String msg) {
					System.out.println(msg);
				}
			};
			Bootstrap b = new Bootstrap();
			b.group(group).channel(NioSocketChannel.class)
					.handler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel ch) {
							ChannelPipeline p = ch.pipeline();
							if (sslCtx != null) {
								p.addLast(sslCtx.newHandler(ch.alloc(), host,
										port));
							}
							p.addLast(new HttpClientCodec(),
									new HttpObjectAggregator(8192), handler);
						}
					});

			channel = b.connect(uri.getHost(), port).sync().channel();
			// ChannelFuture f = channel.closeFuture().await();
			handler.handshakeFuture().sync();
		} catch (Exception e) {
			this.cancel();
			throw e;
		}
	}
	
	/**
	 * 订阅频道（仅限公共频道）
	 * @param channel
	 * @throws ChannelException
	 */
	public void addChannel(String channel)throws ChannelException{
		if(!isAlive())throw new ChannelException("the channel is not active");
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel", channel);
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * 取消订阅（仅限公共频道）
	 * @param channel
	 * @param currency
	 * @throws ChannelException
	 */
	public void removeChannel(String channel,String currency)throws ChannelException{
		if(!isAlive())throw new ChannelException("the channel is not active");
        this.channel.writeAndFlush(new TextWebSocketFrame("{'event':'removeChannel','channel':'"+channel+"'}"));
	}
	
	/**
	 * 委托下单
	 * @param accessKey
	 * @param secretKey
	 * @param price
	 * @param amount
	 * @param coin
	 * @param tradeType 1buy
	 * @throws Exception
	 */
	public void order(double price,double amount,String coin, int tradeType)throws Exception{
		if(!isAlive())throw new ChannelException("the channel is not active");
		
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel", coin.toLowerCase()+"_order");
		data.put("accesskey", accessKey);
		data.put("price", price);
		data.put("amount", amount);
		data.put("tradeType", tradeType);
		
		String secret = EncryDigestUtil.digest(secretKey);
		String sign = EncryDigestUtil.hmacSign(data.toString(), secret);
		
		data.put("sign", sign);
		
		System.out.println(data.toString());
		
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * 取消委托
	 * @param accessKey
	 * @param secretKey
	 * @param id
	 * @param coin
	 * @throws Exception
	 */
	public void cancelOrder(long id,String coin) throws Exception{
		if(!isAlive())throw new ChannelException("the channel is not active");
		
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel", coin.toLowerCase()+"_cancelorder");
		data.put("accesskey", accessKey);
		data.put("id", id);
		
		String secret = EncryDigestUtil.digest(secretKey);
		String sign = EncryDigestUtil.hmacSign(data.toString(), secret);
		
		data.put("sign", sign);
		
		System.out.println(data);
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * 获取委托买单或卖单
	 * @param accessKey
	 * @param secretKey
	 * @param id
	 * @param coin
	 * @throws Exception
	 */
	public void getOrder(long id,String coin) throws Exception{
		if(!isAlive())throw new ChannelException("the channel is not active");
		
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel", coin.toLowerCase()+"_getorder");
		data.put("accesskey", accessKey);
		data.put("id", id);
		
		String secret = EncryDigestUtil.digest(secretKey);
		String sign = EncryDigestUtil.hmacSign(data.toString(), secret);
		
		data.put("sign", sign);
		
		System.out.println(data);
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * 获取多个委托买单或卖单，每次请求返回10条记录
	 * @param accessKey
	 * @param secretKey
	 * @param pageIndex
	 * @param coin
	 * @throws Exception
	 */
	public void getOrders(int pageIndex,int tradeType, String coin) throws Exception{
		if(!isAlive())throw new ChannelException("the channel is not active");
		
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel", coin.toLowerCase()+"_getorders");
		data.put("accesskey", accessKey);
		data.put("tradeType", tradeType);
		data.put("pageIndex", pageIndex);
		
		String secret = EncryDigestUtil.digest(secretKey);
		String sign = EncryDigestUtil.hmacSign(data.toString(), secret);
		
		data.put("sign", sign);
		
		System.out.println(data);
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * (新)获取多个委托买单或卖单，每次请求返回pageSize<=100条记录
	 * @param accessKey
	 * @param secretKey
	 * @param pageIndex
	 * @param pageSize
	 * @param coin
	 * @throws Exception
	 */
	public void getOrdersNew(int pageIndex,int pageSize,int tradeType,String coin) throws Exception{
		if(!isAlive())throw new ChannelException("the channel is not active");
		
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel", coin.toLowerCase()+"_getordersnew");
		data.put("accesskey", accessKey);
		data.put("tradeType", tradeType);
		data.put("pageIndex", pageIndex);
		data.put("pageSize", pageSize);
		
		String secret = EncryDigestUtil.digest(secretKey);
		String sign = EncryDigestUtil.hmacSign(data.toString(), secret);
		
		data.put("sign", sign);
		
		System.out.println(data);
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * 与getOrdersNew的区别是取消tradeType字段过滤，可同时获取买单和卖单，每次请求返回pageSize<=100条记录
	 * @param accessKey
	 * @param secretKey
	 * @param pageIndex
	 * @param pageSize
	 * @param coin
	 * @throws Exception
	 */
	public void getOrdersIgnoreTradeType(int pageIndex,int pageSize,String coin) throws Exception{
		if(!isAlive())throw new ChannelException("the channel is not active");
		
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel", coin.toLowerCase()+"_getordersignoretradetype");
		data.put("accesskey", accessKey);
		data.put("pageIndex", pageIndex);
		data.put("pageSize", pageSize);
		
		String secret = EncryDigestUtil.digest(secretKey);
		System.out.println("getOrdersIgnoreTradeType:" + data.toString());
		String sign = EncryDigestUtil.hmacSign(data.toString(), secret);
		
		data.put("sign", sign);
		
		System.out.println(data);
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * 获取未成交或部份成交的买单和卖单，每次请求返回pageSize<=100条记录
	 * @param accessKey
	 * @param secretKey
	 * @param pageIndex
	 * @param pageSize
	 * @param coin
	 * @throws Exception
	 */
	public void getUnfinishedOrdersIgnoreTradeType(int pageIndex,int pageSize,String coin) throws Exception{
		if(!isAlive())throw new ChannelException("the channel is not active");
		
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel", coin.toLowerCase()+"_getunfinishedordersignoretradetype");
		data.put("accesskey", accessKey);
		data.put("pageIndex", pageIndex);
		data.put("pageSize", pageSize);
		
		String secret = EncryDigestUtil.digest(secretKey);
		String sign = EncryDigestUtil.hmacSign(data.toString(), secret);
		
		data.put("sign", sign);
		
		System.out.println(data);
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * 获取某个币种的充值地址
	 * @param accessKey
	 * @param coin
	 * @throws Exception
	 */
	public void getUserAddress(String coin) throws Exception{
		if(!isAlive())throw new ChannelException("the channel is not active");
		
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel", coin.toLowerCase()+"_getuseraddress");
		data.put("accesskey", accessKey);
		
		String secret = EncryDigestUtil.digest(secretKey);
		String sign = EncryDigestUtil.hmacSign(data.toString(), secret);
		
		data.put("sign", sign);
		
		System.out.println(data);
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * 获取认证的提现地址
	 * @param accessKey
	 * @param coin
	 * @throws Exception
	 */
	public void getWithdrawAddress(String coin) throws Exception{
		if(!isAlive())throw new ChannelException("the channel is not active");
		
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel", coin.toLowerCase()+"_getwithdrawaddress");
		data.put("accesskey", accessKey);
		
		String secret = EncryDigestUtil.digest(secretKey);
		String sign = EncryDigestUtil.hmacSign(data.toString(), secret);
		
		data.put("sign", sign);
		
		System.out.println(data);
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * 获取提现的记录
	 * @param accessKey
	 * @param coin
	 * @throws Exception
	 */
	public void getWithdrawRecord(String coin, int pageIndex, int pageSize) throws Exception{
		if(!isAlive())throw new ChannelException("the channel is not active");
		
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel", coin.toLowerCase()+"_getwithdrawrecord");
		data.put("accesskey", accessKey);
		data.put("pageIndex", pageIndex);
		data.put("pageSize", pageSize);
		
		String secret = EncryDigestUtil.digest(secretKey);
		String sign = EncryDigestUtil.hmacSign(data.toString(), secret);
		
		data.put("sign", sign);
		
		System.out.println(data);
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * 取消提现
	 * @param accessKey
	 * @param coin
	 * @throws Exception
	 */
	public void cancelWithdraw(String coin, String downloadId) throws Exception{
		if(!isAlive())throw new ChannelException("the channel is not active");
		
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel", coin.toLowerCase()+"_cancelwithdraw");
		data.put("accesskey", accessKey);
		data.put("downloadId", downloadId);
		data.put("safePwd", payPass);
		
		String secret = EncryDigestUtil.digest(secretKey);
		String sign = EncryDigestUtil.hmacSign(data.toString(), secret);
		
		data.put("sign", sign);
		
		System.out.println(data);
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * 提现
	 * @param coin	币种
	 * @param amount	提现金额
	 * @param fees		提现矿工费
	 * @param receiveAddr	提现地址
	 * @throws Exception
	 */
	public void withdraw(String coin, BigDecimal amount, BigDecimal fees, String receiveAddr) throws Exception{
		if(!isAlive())throw new ChannelException("the channel is not active");
		
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel", coin.toLowerCase()+"_withdraw");
		data.put("accesskey", accessKey);
		data.put("amount", amount+"");
		data.put("fees", fees+"");
		data.put("receiveAddr", receiveAddr);
		data.put("safePwd", payPass);
		
		String secret = EncryDigestUtil.digest(secretKey);
		String sign = EncryDigestUtil.hmacSign(data.toString(), secret);
		
		data.put("sign", sign);
		
		System.out.println(data);
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * 获取用户资金信息
	 * @param accessKey
	 * @param secretKey
	 */
	public void getAccountInfo(){
		JSONObject data=new JSONObject();
		data.put("event", "addChannel");
		data.put("channel","getaccountinfo");
		data.put("accesskey", accessKey);
		data.put("no", null);
		
		String secret = EncryDigestUtil.digest(secretKey);
		String sign = EncryDigestUtil.hmacSign(data.toString(), secret);
		
		data.put("sign", sign);
		
		System.out.println(data);
        this.channel.writeAndFlush(new TextWebSocketFrame(data.toString()));
	}
	
	/**
	 * 注销客户端
	 */
	public void cancel(){
		if(group!=null)group.shutdownGracefully();
	}
	/**
	 * 判断客户端是否保持激活状态
	 * @return
	 */
	public boolean isAlive(){
		return this.channel!=null&&this.channel.isActive()?true:false;
	}
	
	/**
	 * 测试帐号:
	 * API访问密匙(Access Key)： d31f15d5-xxxx-xxxx-xxxx-5ab5e6326b2e
	 * API私有密匙(Secret Key)： c1639fa5-xxxx-xxxx-xxxx-f42759830a19[仅显示一次]
	 * @param args
	 * @throws Exception
	 */
    public void doTask(WebSocketClient client) throws Exception {
    	System.out.println("websocket通讯地址：" + serverUrl);
			try {
//				client.addChannel("ltcbtc_ticker");//ticker
				client.addChannel("ltcbtc_depth");//深度
//				client.addChannel("ltcbtc_trades");//历史成交
				
//    			client.order( 0.019258, 1, "ethbtc", 0);
//    			client.order( 0.009258, 1, "ltcbtc", 1);
//    			client.cancelOrder(20151006160133624L , "ethbtc");
//    			client.getOrder(20151006160133556L , "ethbtc");
//    			client.getOrders(1,1 , "ethbtc");
//    			client.getOrdersIgnoreTradeType(1,10 , "ethbtc");
//    			client.getUnfinishedOrdersIgnoreTradeType(1,10 , "ethbtc");
//				client.getOrdersNew( 1,10, 1, "ethbtc");
//				client.cancelWithdraw("ethbtc", "20160425916");
				
//				client.getAccountInfo();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//    	}
    }
    
}
