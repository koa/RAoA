/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.server.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.bergturbenthal.raoa.server.AlbumAccess;
import ch.bergturbenthal.raoa.server.model.StorageStatistics;

/**
 * returns statistical data about this server
 * 
 */
@Controller
@RequestMapping("/statistics")
public class StatisticsController {
	@Autowired
	private AlbumAccess dataAccess;

	@RequestMapping(method = RequestMethod.GET)
	public @ResponseBody
	StorageStatistics readStatistics() {
		return dataAccess.getStatistics();
	}

}
