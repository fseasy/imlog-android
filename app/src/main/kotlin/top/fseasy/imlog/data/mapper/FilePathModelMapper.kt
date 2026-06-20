package top.fseasy.imlog.data.mapper

import android.content.Context
import top.fseasy.imlog.domain.model.FilePathModel
import top.fseasy.imlog.domain.model.InternalLocation
import top.fseasy.imlog.domain.util.resolveSubPaths
import java.io.File



// NOTE: we can't define SharedStorageOnly mapper, as it needs the uri root of user,
// which can't be accessed in public.
// And I think it's unnecessary to put it here by some hack ways like create a public api.
// The uri file should be mainly used in that class internal.