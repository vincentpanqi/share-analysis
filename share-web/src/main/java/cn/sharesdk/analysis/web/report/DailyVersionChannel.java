package cn.sharesdk.analysis.web.report;

import cn.sharesdk.analysis.web.ActionContext;
import cn.sharesdk.analysis.web.Constants;
import cn.sharesdk.analysis.web.util.ActiveDeviceUtils;
import cn.sharesdk.analysis.web.util.DateUtils;
import cn.sharesdk.analysis.web.util.RedisUtil;
import com.lamfire.json.JSON;
import com.lamfire.json.JSONArray;
import com.lamfire.logger.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by dempe on 14-6-12.
 */
public class DailyVersionChannel extends BaseAction {

    private static Logger logger = Logger.getLogger(DailyReport.class);



    @Override
    public byte[] execute(ActionContext context, byte[] message, Map<String, List<String>> params) {
        String appkey = getParameter(params, "appkey");
        System.out.println("appkey:" + appkey);
        return sumDailyReport(appkey, params).toBytes();
    }

    /**
     * sum_daily report.
     * <p/>
     * <br/>
     * url :
     * report?report_name=sum_daily&appkey=b5558eab134cf306fdcdc3a6746afa25&startDate=20140528&endDate=20140531
     *
     * @param _appkey
     * @return
     */
    private JSONArray sumDailyReport(String _appkey, Map<String, List<String>> params) {
        String _startDate = getParameter(params, "startDate");
        String _endDate = getParameter(params, "endDate");

        int _istartDate = Integer.parseInt(_startDate);
        int _iendDate = Integer.parseInt(_endDate);

        String _create_date = DateUtils.getDateRegx(_startDate, _endDate);

        String _version = getParameter(params, "version");
        String _channel = getParameter(params, "channel");

        String key = _appkey + Constants.SPLIT + _version + Constants.SPLIT + _channel + Constants.SPLIT + _create_date;

        JSONArray jsonArray = new JSONArray();

        Jedis jedis = null;
        try {
            jedis = RedisUtil.getJedis();
            Set<String> hkeys = jedis.keys(key);
            Iterator<String> it = hkeys.iterator();
            Pipeline pipeline = jedis.pipelined();
            while (it.hasNext()) {
                JSON map = new JSON();
                String hkey = it.next();
                String[] cols = hkey.split(Constants.SPLIT);

                int createDate = Integer.parseInt(cols[3]);
                if (!(createDate >= _istartDate && createDate <= _iendDate)) {
                    continue;
                }

                String appkey = cols[0];
                String appver = cols[1];
                String chanelname = cols[2];
                String create_date = cols[3];
                map.put("appkey", appkey);
                map.put("appver", appver);
                map.put("chanelname", chanelname);
                map.put("create_date", create_date);
                Response<List<String>> fieldValueResponse = pipeline.hmget(hkey, "run_num", "new_device", "duration");
                pipeline.sync();
                List<String> fieldValues = fieldValueResponse.get();
                String run_num = fieldValues.get(0);
                String new_device = fieldValues.get(1);
                String duration = fieldValues.get(2);

                int active_device = ActiveDeviceUtils.getActiveDevice(appkey,appver,chanelname,create_date);
                map.put("run_num", run_num);
                map.put("new_device", new_device);
                map.put("active_device",active_device);
                map.put("duration", duration);
                jsonArray.add(map);
            }
        } finally {
            if (jedis != null) {
                RedisUtil.returnJedis(jedis);
            }
        }

        return jsonArray;
    }

}