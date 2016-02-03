package com.cn.server.module.player.service;

import org.jboss.netty.channel.Channel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.cn.common.core.exception.ErrorCodeException;
import com.cn.common.core.model.ResultCode;
import com.cn.common.module.player.proto.PlayerModule;
import com.cn.common.module.player.proto.PlayerModule.PlayerResponse;
import com.cn.server.channel.ChannelManager;
import com.cn.server.module.player.dao.PlayerDao;
import com.cn.server.module.player.dao.entity.Player;

/**
 * 玩家服务
 * 
 * @author -琴兽-
 * 
 */
@Component
public class PlayerServiceImpl implements PlayerService {

	@Autowired
	private PlayerDao playerDao;

	@Override
	public PlayerResponse registerAndLogin(Channel channel, String playerName, String passward) {

		Player existplayer = playerDao.getPlayerByName(playerName);

		// 玩家名已被占用
		if (existplayer != null) {
			throw new ErrorCodeException(ResultCode.PLAYER_EXIST);
		}

		// 创建新帐号
		Player player = new Player();
		player.setPlayerName(playerName);
		player.setPassward(passward);
		player = playerDao.createPlayer(player);

		//顺便登录
		return login(channel, playerName, passward);
	}

	@Override
	public PlayerResponse login(Channel channel, String playerName, String passward) {

		// 判断当前会话是否已登录
		if (channel.getAttachment() != null) {
			throw new ErrorCodeException(ResultCode.HAS_LOGIN);
		}

		// 玩家不存在
		Player player = playerDao.getPlayerByName(playerName);
		if (player == null) {
			throw new ErrorCodeException(ResultCode.PLAYER_NO_EXIST);
		}

		// 密码错误
		if (!player.getPassward().equals(passward)) {
			throw new ErrorCodeException(ResultCode.PASSWARD_ERROR);
		}

		// 判断是否在其他地方登录过
		boolean onlinePlayer = ChannelManager.isOnlinePlayer(player.getPlayerId());
		if (onlinePlayer) {
			Channel oldChannel = ChannelManager.removeChannel(player.getPlayerId());
			oldChannel.setAttachment(null);
			// 踢下线
			oldChannel.close();
		}

		// 加入在线玩家会话
		if (ChannelManager.putChannel(player.getPlayerId(), channel)) {
			channel.setAttachment(player);
		} else {
			throw new ErrorCodeException(ResultCode.LOGIN_FAIL);
		}

		// 创建Response传输对象返回
		PlayerResponse playerResponse = PlayerModule.PlayerResponse.newBuilder()
				.setPlayerId(player.getPlayerId())
				.setPlayerName(player.getPlayerName())
				.setLevel(player.getLevel())
				.setExp(player.getExp()).build();
		return playerResponse;
	}

}
